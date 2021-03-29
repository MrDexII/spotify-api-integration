package com.andrzej.spotifyapi.service;

import com.andrzej.spotifyapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class HomeService {

    private final Logger logger = LoggerFactory.getLogger(HomeService.class);

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

    public List<Item> getListOfAllTracks(String id, OAuth2AuthorizedClient authorizedClient) {
        final ExecutorService executorService = Executors.newFixedThreadPool(100);

        BlockingQueue<String> allAlbumsIDs = new LinkedBlockingQueue<>();

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

        executorService.execute(getAllAlbumsFromArtist);
        final Future<List<Item>> listOfAllTracks = executorService.submit(getAllTracksFromAlbums);

        executorService.shutdown();

        try {
            return listOfAllTracks.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    private SpotifySearchForItems wrapperForWaitForResults(URI uri, OAuth2AuthorizedClient authorizedClient) throws InterruptedException {
        SpotifySearchForItems tracks = new SpotifySearchForItems();
        long secondsToWait;

        do {
            try {
                secondsToWait = 0L;
                tracks = createResponseEntity(uri, authorizedClient, SpotifySearchForItems.class);
            } catch (HttpClientErrorException.TooManyRequests e) {
                e.printStackTrace();
                HttpHeaders responseHeaders = e.getResponseHeaders();
                if (responseHeaders == null) {
                    return tracks;
                }
                final String waitTime = Objects.requireNonNull(responseHeaders.getFirst("Retry-After"));
                //I thing Spotify API is rounding wait time to lowe values,
                //so waiting time is higher, that's "+600"
                secondsToWait = Long.parseLong(waitTime);
                Thread.sleep(secondsToWait * 1000 + 600);
                logger.warn("To many request to API");
                logger.warn("Wait time: " + (secondsToWait + 0.6) + "s");
            }
        }
        while (secondsToWait != 0);

        return tracks;
    }

    private <T> T createResponseEntity(URI uri, OAuth2AuthorizedClient authorizedClient, Class<T> trackSpotifySearchClass) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", getToken(authorizedClient));
        HttpEntity<HttpHeaders> httpEntity = new HttpEntity<>(headers);

        ResponseEntity<T> exchange = restTemplate.exchange(uri,
                HttpMethod.GET,
                httpEntity,
                trackSpotifySearchClass);
        return exchange.getBody();
    }

    private String getToken(OAuth2AuthorizedClient authorizedClient) {
        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
        return String.format("%s %s", accessToken.getTokenType().getValue(), accessToken.getTokenValue());
    }
}
