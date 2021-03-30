package com.andrzej.spotifyapi.service;

import com.andrzej.spotifyapi.model.SpotifySearchForItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Objects;

public class Utils {

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    public static SpotifySearchForItems wrapperForWaitForResults(URI uri, OAuth2AuthorizedClient authorizedClient) throws InterruptedException {
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

    public static <T> T createResponseEntity(URI uri, OAuth2AuthorizedClient authorizedClient, Class<T> trackSpotifySearchClass) {
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

    public static String getToken(OAuth2AuthorizedClient authorizedClient) {
        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
        return String.format("%s %s", accessToken.getTokenType().getValue(), accessToken.getTokenValue());
    }
}
