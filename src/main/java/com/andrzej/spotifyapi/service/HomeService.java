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
                        uri = new URI("https://api.spotify.com/v1/artists/" + id + "/albums?market=PL&limit=" + limit + "&offset=0");
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
                            //System.out.println("Dodałem: "+ Thread.currentThread().getName());
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (Objects.requireNonNull(result).getAdditionalProperties().get("next") != null);
        };

        Callable<List<Item>> getAllTracksFromAlbums = () -> {
            URI uri;
            SpotifySearchForItems result = null;
            final CopyOnWriteArrayList<Item> allTracks = new CopyOnWriteArrayList<>();

            do {
                final String albumId = allAlbumsIDs.poll(10, TimeUnit.SECONDS);
                //if (result == null) {
                uri = new URI("https://api.spotify.com/v1/albums/" + albumId + "/tracks");
//                } else {
//                    uri = new URI((String) result.getAdditionalProperties().get("next"));
//                }
                result = wrapperForWaitForResults(uri, authorizedClient);
                allTracks.addAll(result.getItems());
                //System.out.println("Odebrałem: " + Thread.currentThread().getName());
            } while (allAlbumsIDs.size() != 0);

            return allTracks;
        };

//        Callable<CopyOnWriteArrayList<Item>> start = () -> {
//            CopyOnWriteArrayList<Item> listOfAllTracks = null;
//            do {
//                try {
//                    final String albumId = allAlbumsIDs.poll(1, TimeUnit.SECONDS);
//                    if (albumId == null) break;
//                    System.out.println(albumId);
//                    final Future<CopyOnWriteArrayList<Item>> listOfFutureTracks = executorService.submit(new MyCallable(albumId, authorizedClient));
//                    System.out.println(listOfFutureTracks);
//                    listOfAllTracks.addAll(listOfFutureTracks.get());
//                } catch (InterruptedException | ExecutionException e) {
//                    e.printStackTrace();
//                }
//            } while (allAlbumsIDs.size() != 0);
//            return listOfAllTracks;
//        };

        //executorService.prestartAllCoreThreads();

        executorService.prestartAllCoreThreads();

        executorService.execute(getAllAlbumsFromArtist);

        Set<Item> finalList = new CopyOnWriteArraySet<>();

        do {
            try {
                final String albumId = allAlbumsIDs.poll(1, TimeUnit.SECONDS);
                //System.out.println(allAlbumsIDs.size());
//                System.out.println(albumId);
                if (albumId == null) break;
                final Future<CopyOnWriteArrayList<Item>> futureListOfAllTracks = executorService.submit(new MyCallable(albumId, authorizedClient));
//                System.out.println(futureListOfAllTracks);
                finalList.addAll(futureListOfAllTracks.get());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        } while (true);
        //final Future<CopyOnWriteArrayList<Item>> futureListOfAllTracks = executorService.submit(start);
        executorService.shutdown();
        return finalList;
    }

//    private SpotifySearchForItems wrapperForWaitForResults(URI uri, OAuth2AuthorizedClient authorizedClient) throws InterruptedException {
//        SpotifySearchForItems tracks = new SpotifySearchForItems();
//        long secondsToWait;
//
//        do {
//            try {
//                secondsToWait = 0L;
//                tracks = createResponseEntity(uri, authorizedClient, SpotifySearchForItems.class);
//            } catch (HttpClientErrorException.TooManyRequests e) {
//                e.printStackTrace();
//                HttpHeaders responseHeaders = e.getResponseHeaders();
//                if (responseHeaders == null) {
//                    return tracks;
//                }
//                final String waitTime = Objects.requireNonNull(responseHeaders.getFirst("Retry-After"));
//                //I thing Spotify API is rounding wait time to lowe values,
//                //so waiting time is higher, that's "+600"
//                secondsToWait = Long.parseLong(waitTime);
//                Thread.sleep(secondsToWait * 1000 + 600);
//                logger.warn("To many request to API");
//                logger.warn("Wait time: " + (secondsToWait + 0.6) + "s");
//            }
//        }
//        while (secondsToWait != 0);
//
//        return tracks;
//    }
//
//    private <T> T createResponseEntity(URI uri, OAuth2AuthorizedClient authorizedClient, Class<T> trackSpotifySearchClass) {
//        RestTemplate restTemplate = new RestTemplate();
//        HttpHeaders headers = new HttpHeaders();
//        headers.add("Authorization", getToken(authorizedClient));
//        HttpEntity<HttpHeaders> httpEntity = new HttpEntity<>(headers);
//
//        ResponseEntity<T> exchange = restTemplate.exchange(uri,
//                HttpMethod.GET,
//                httpEntity,
//                trackSpotifySearchClass);
//        return exchange.getBody();
//    }
//
//    private String getToken(OAuth2AuthorizedClient authorizedClient) {
//        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
//        return String.format("%s %s", accessToken.getTokenType().getValue(), accessToken.getTokenValue());
//    }
}
