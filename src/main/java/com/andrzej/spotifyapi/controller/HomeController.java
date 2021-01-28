package com.andrzej.spotifyapi.controller;

import com.andrzej.spotifyapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    private final Logger logger = LoggerFactory.getLogger(HomeController.class);

    @GetMapping("/")
    public String index(Model model,
                        @RegisteredOAuth2AuthorizedClient OAuth2AuthorizedClient authorizedClient,
                        @AuthenticationPrincipal OAuth2User oauth2User) {
        model.addAttribute("userName", oauth2User.getName());
        model.addAttribute("clientName", authorizedClient.getClientRegistration().getClientName());
        model.addAttribute("userAttributes", oauth2User.getAttributes());
        return "index";
    }

    @GetMapping(value = "/artist/{name}")
    public String artist(@PathVariable("name") String name,
                         @RegisteredOAuth2AuthorizedClient OAuth2AuthorizedClient authorizedClient,
                         Model model) throws URISyntaxException {

        name = name.replace(" ", "%20");

        URI uri = new URI("https://api.spotify.com/v1/search?q=" + name + "&type=artist&limit=10&offset=0");

        ArtistSpotifySearch artists = createResponseEntity(uri, authorizedClient, ArtistSpotifySearch.class);

        List<Item> artistsList = artists.getArtists().getItems();
        Comparator<Item> itemsComparator = Comparator.comparingInt(i -> i.getFollowers().getTotal());
        artistsList = artistsList.stream().sorted(itemsComparator.reversed()).collect(Collectors.toList());
        artists.getArtists().setItems(artistsList);

        model.addAttribute("artistSpotifySearch", artists);

        return "artist";
    }

    @GetMapping("/top10/{id}")
    public String getTop(@PathVariable("id") String id,
                         @RequestParam("author") String authorName,
                         @RegisteredOAuth2AuthorizedClient OAuth2AuthorizedClient authorizedClient,
                         Model model) throws URISyntaxException {

        URI uri = new URI("https://api.spotify.com/v1/artists/" + id + "/top-tracks?country=PL");
        TrackFromAlbumSpotifySearch trackObject = createResponseEntity(uri, authorizedClient, TrackFromAlbumSpotifySearch.class);
        List<Track> tracks = trackObject.getTracks();

        model.addAttribute("topTenTracks", tracks);
        model.addAttribute("authorName", authorName);

        return "track";
    }

    @GetMapping("/all-tracks/{id}")
    public String getAllTracks(@PathVariable("id") String id,
                               @RequestParam("author") String authorName,
                               @RegisteredOAuth2AuthorizedClient OAuth2AuthorizedClient authorizedClient,
                               Model model) throws URISyntaxException, InterruptedException {
        int limit = 50;

        //getting all albums from artist
        URI uri = new URI("https://api.spotify.com/v1/artists/" + id + "/albums?market=PL&limit=" + limit + "&offset=0");
        SpotifySearchForItems tracks;
        tracks = wrapperForWaitForResults(uri, authorizedClient);

        List<String> listOfAlbumsIds = tracks.getItems().stream().map(Item::getId).collect(Collectors.toList());

        while (tracks.getAdditionalProperties().get("next") != null) {
            uri = new URI((String) tracks.getAdditionalProperties().get("next"));
            tracks = wrapperForWaitForResults(uri, authorizedClient);
            listOfAlbumsIds.addAll(tracks.getItems().stream().map(Item::getId).collect(Collectors.toList()));
        }

        //getting all tracks from albums
        List<Item> listOfAllTracks = new LinkedList<>();

        for (String albumId : listOfAlbumsIds) {
            uri = new URI("https://api.spotify.com/v1/albums/" + albumId + "/tracks");
            tracks = wrapperForWaitForResults(uri, authorizedClient);
            listOfAllTracks.addAll(tracks.getItems());

            while ((String) tracks.getAdditionalProperties().get("next") != null) {
                uri = new URI((String) tracks.getAdditionalProperties().get("next"));
                tracks = wrapperForWaitForResults(uri, authorizedClient);
                listOfAllTracks.addAll(tracks.getItems());
            }
        }

        model.addAttribute("allTracks", listOfAllTracks);
        model.addAttribute("artistName", authorName);

        return "all-tracks";
    }

    private SpotifySearchForItems wrapperForWaitForResults(URI uri, OAuth2AuthorizedClient authorizedClient) throws InterruptedException {
        SpotifySearchForItems tracks;
        try {
            tracks = createResponseEntity(uri, authorizedClient, SpotifySearchForItems.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            HttpHeaders responseHeaders = e.getResponseHeaders();
            long secondsToWait = Long.parseLong(responseHeaders.getFirst("Retry-After"));
            Thread.sleep(secondsToWait * 1000 + 600);
            tracks = createResponseEntity(uri, authorizedClient, SpotifySearchForItems.class);
            logger.info("Trzeba bylo czekac " + (secondsToWait + 0.6) + "s");
        }
        return tracks;
    }

    private <T> T createResponseEntity(URI uri, OAuth2AuthorizedClient authorizedClient, Class<T> trackSpotifySearchClass) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", getToken(authorizedClient));
        HttpEntity httpEntity = new HttpEntity(headers);

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
