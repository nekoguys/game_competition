package com.groudina.ten.demo.controllers;

import com.groudina.ten.demo.datasource.DbCompetitionsRepository;
import com.groudina.ten.demo.datasource.DbUserRepository;
import com.groudina.ten.demo.dto.*;
import com.groudina.ten.demo.exceptions.CaptainAlreadyCreatedGameException;
import com.groudina.ten.demo.exceptions.IllegalGameStateException;
import com.groudina.ten.demo.models.DbCompetition;
import com.groudina.ten.demo.services.IAddTeamToCompetitionService;
import com.groudina.ten.demo.services.IEntitiesMapper;
import com.groudina.ten.demo.services.ITeamConnectionNotifyService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import java.security.Principal;
import java.util.List;

@Log4j2
@RequestMapping(path="/api/competitions", produces = {MediaType.APPLICATION_JSON_VALUE})
@CrossOrigin(origins = {"*"}, maxAge = 3600)
@RestController
public class CompetitionsController {
    private DbCompetitionsRepository competitionsRepository;
    private DbUserRepository userRepository;
    private IEntitiesMapper<NewCompetition, DbCompetition> competitionMapper;
    private IAddTeamToCompetitionService teamJoinService;
    private ITeamConnectionNotifyService teamConnectionNotifyService;

    public CompetitionsController(@Autowired DbCompetitionsRepository repository,
                                  @Autowired DbUserRepository userRepository,
                                  @Autowired IEntitiesMapper<NewCompetition, DbCompetition> mapper,
                                  @Autowired IAddTeamToCompetitionService teamJoinService,
                                  @Autowired ITeamConnectionNotifyService teamConnectionNotifyService) {
        this.competitionsRepository = repository;
        this.userRepository = userRepository;
        this.competitionMapper = mapper;
        this.teamJoinService = teamJoinService;
        this.teamConnectionNotifyService = teamConnectionNotifyService;
    }

    @PostMapping(value = "/create")
    @PreAuthorize("hasRole('TEACHER')")
    public Mono<ResponseEntity> createCompetition(Mono<Principal> principalMono, @Valid @RequestBody NewCompetition competition) {
        return principalMono.map(principal -> {
            log.error(principal.getName());
            return principal.getName();
        }).flatMap(userEmail -> {
            return userRepository.findOneByEmail(userEmail);
        }).flatMap(dbUser -> {
            var dbCompetition = competitionMapper.map(competition, List.of(Pair.of("owner", dbUser)));
            return competitionsRepository.save(dbCompetition);
        }).map(newCompetition -> {
            return ResponseEntity.ok(ResponseMessage.of("Competition Created Successfully"));
        });
    }

    @PostMapping(value = "/create_team")
    @PreAuthorize("hasRole('STUDENT')")
    public Mono<ResponseEntity<ResponseMessage>> joinTeam(@Valid @RequestBody NewTeam newTeam) {
        return this.teamJoinService.addTeamToCompetition(newTeam).map(team -> {
            return ResponseEntity.ok(ResponseMessage.of("Team created successfully"));
        })
                .onErrorReturn(CaptainAlreadyCreatedGameException.class,
                        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResponseMessage.of("Captain is in another team already")))
                .onErrorReturn(IllegalGameStateException.class,
                        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResponseMessage.of("Illegal game state")))
                .onErrorReturn(CaptainAlreadyCreatedGameException.class,
                        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResponseMessage.of("Game not found or user not found")));
    }

    @PostMapping(value = "/check_pin")
    @PreAuthorize("hasRole('STUDENT')")
    public Mono<ResponseEntity<GamePinCheckResponse>> checkIfGameExists(@Valid @RequestBody GamePinCheckRequest pinCheck) {
        return competitionsRepository.findByPin(pinCheck.getPin()).map(comp -> {
            if (comp.getState() != DbCompetition.State.Registration) {
                return ResponseEntity.ok(GamePinCheckResponse.of(false));
            }
            return ResponseEntity.ok(GamePinCheckResponse.of(true));
        }).defaultIfEmpty(ResponseEntity.ok(GamePinCheckResponse.of(false)));
    }

    @RequestMapping(value = "/team_join_events/{pin}", produces = {MediaType.TEXT_EVENT_STREAM_VALUE})
    public Flux<ServerSentEvent<?>> subscribeToTeamJoinEvents(@PathVariable String pin) {
        return teamConnectionNotifyService.getTeamEventForGame(pin).map(e -> ServerSentEvent.builder(e).build());
    }
}



