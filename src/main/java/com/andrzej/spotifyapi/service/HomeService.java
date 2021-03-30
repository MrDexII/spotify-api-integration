package com.andrzej.spotifyapi.service;

import com.andrzej.spotifyapi.model.*;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.andrzej.spotifyapi.service.Utils.createResponseEntity;
import static com.andrzej.spotifyapi.service.Utils.wrapperForWaitForResults;

@Service
public class HomeService {


    public List<Item> getArtists(String name, OAuth2AuthorizedClient authorizedClient) throws URISyntaxException {
        name = name.replace(" ", "%20");

        URI uri = new URI("https://api.spotify.com/v1/search?q=" + name + "&type=artist&limit=10&offset=0");

        ArtistSpotifySearch artists = createResponseEntity(uri, authorizedClient, ArtistSpotifySearch.class);

        List<Item> artistsList = artists.getArtists().getItems();
        Comparator<Item> itemsComparator = Comparator.comparingInt(i -> i.getFollowers().getTotal());
        artistsList = artistsList.stream().sorted(itemsComparator.reversed()).collect(Collectors.toList());
        artists.getArtists().setItems(artistsList);

        return artistsList;
    }

    public List<Track> getTopTracks(String id, OAuth2AuthorizedClient authorizedClient) throws URISyntaxException {
        URI uri = new URI("https://api.spotify.com/v1/artists/" + id + "/top-tracks?country=PL");
        TrackFromAlbumSpotifySearch trackObject = createResponseEntity(uri, authorizedClient, TrackFromAlbumSpotifySearch.class);
        return trackObject.getTracks();
    }

    public Set<Item> getListOfAllTracks(String id, OAuth2AuthorizedClient authorizedClient) {
        BlockingQueue<Runnable> allAlbumsIDsRunnable = new LinkedBlockingQueue<>(50);
        BlockingQueue<String> allAlbumsIDs = new LinkedBlockingQueue<>();
        Set<Item> resultSet = new CopyOnWriteArraySet<>();

        CustomExecutorService executorService = new CustomExecutorService(5, 10, 10, TimeUnit.SECONDS, allAlbumsIDsRunnable);

        executorService.setRejectedExecutionHandler((r, executor) -> {
            try {
                System.out.println("Request rejected");
                TimeUnit.SECONDS.sleep(1);
            } catch (Exception e) {
                e.printStackTrace();
            }
            executor.execute(r);
        });

        Runnable getAllAlbumsFromArtist = () -> {
            int limit = 50;
            URI uri = null;
            SpotifySearchForItems result = null;

            do {
                if (result == null) {
                    try {
                        uri = new URI("https://api.spotify.com/v1/artists/" +
                                id +
                                "/albums?market=PL&limit=" + limit + "&offset=0");
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        uri = new URI((String) result.getAdditionalProperties().get("next"));
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    }
                }

                try {
                    result = wrapperForWaitForResults(uri, authorizedClient);
                    result.getItems().stream().map(Item::getId).forEach(item -> {
                        try {
                            allAlbumsIDs.put(item);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (Objects.requireNonNull(result).getAdditionalProperties().get("next") != null);
        };

        executorService.prestartAllCoreThreads();
        executorService.execute(getAllAlbumsFromArtist);

        do {
            try {
                final String albumId = allAlbumsIDs.poll(1, TimeUnit.SECONDS);
                if (albumId == null) break;
                final Future<CopyOnWriteArrayList<Item>> futureListOfAllTracks = executorService.submit(new AlbumTracksCallable(albumId, authorizedClient));
                resultSet.addAll(futureListOfAllTracks.get());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        } while (true);
        
        executorService.shutdown();
        return resultSet;
    }
}
