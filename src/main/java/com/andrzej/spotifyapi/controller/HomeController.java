package com.andrzej.spotifyapi.controller;

import com.andrzej.spotifyapi.service.HomeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URISyntaxException;

@Controller
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping("/")
    public String index(Model model,
                        @RegisteredOAuth2AuthorizedClient OAuth2AuthorizedClient authorizedClient,
                        @AuthenticationPrincipal OAuth2User oauth2User) {
        model.addAttribute("userName", oauth2User.getName());
        model.addAttribute("clientName", authorizedClient.getClientRegistration().getClientName());
        return "index";
    }

    @GetMapping(value = "/artist")
    public String artist(@RequestParam String artName,
                         @RegisteredOAuth2AuthorizedClient OAuth2AuthorizedClient authorizedClient,
                         Model model) throws URISyntaxException {
        model.addAttribute("artistSpotifySearch", homeService.getArtists(artName, authorizedClient));
        return "artist";
    }

    @GetMapping("/top10/{id}")
    public String getTop(@PathVariable("id") String id,
                         @RequestParam("author") String authorName,
                         @RegisteredOAuth2AuthorizedClient OAuth2AuthorizedClient authorizedClient,
                         Model model) throws URISyntaxException {
        model.addAttribute("topTenTracks", homeService.getTopTracks(id, authorizedClient));
        model.addAttribute("authorName", authorName);
        return "track";
    }

    @GetMapping("/all-tracks/{id}")
    public String getAllTracks(@PathVariable("id") String id,
                               @RequestParam("author") String authorName,
                               @RegisteredOAuth2AuthorizedClient OAuth2AuthorizedClient authorizedClient,
                               Model model) throws URISyntaxException, InterruptedException {
        model.addAttribute("allTracks", homeService.getListOfAllTracks(id, authorizedClient));
        model.addAttribute("artistName", authorName);
        return "all-tracks";
    }
}
