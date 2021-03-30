package com.andrzej.spotifyapi.service;

import com.andrzej.spotifyapi.model.Item;
import com.andrzej.spotifyapi.model.SpotifySearchForItems;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;

import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.andrzej.spotifyapi.service.Utils.wrapperForWaitForResults;

public class AlbumTracksCallable implements Callable<CopyOnWriteArrayList<Item>> {

    private final String albumId;
    private final OAuth2AuthorizedClient authorizedClient;

    public AlbumTracksCallable(String albumId, OAuth2AuthorizedClient authorizedClient) {
        this.albumId = albumId;
        this.authorizedClient = authorizedClient;
    }

    @Override
    public CopyOnWriteArrayList<Item> call() throws Exception {
        URI uri;
        SpotifySearchForItems result = null;
        uri = new URI("https://api.spotify.com/v1/albums/" + albumId + "/tracks");

        result = wrapperForWaitForResults(uri, authorizedClient);
        final CopyOnWriteArrayList<Item> allTracks = new CopyOnWriteArrayList<>(result.getItems());

        while (result.getAdditionalProperties().get("next") != null) {
            uri = new URI((String) result.getAdditionalProperties().get("next"));
            result = wrapperForWaitForResults(uri, authorizedClient);
            allTracks.addAll(result.getItems());
        }
        return allTracks;
    }
}
