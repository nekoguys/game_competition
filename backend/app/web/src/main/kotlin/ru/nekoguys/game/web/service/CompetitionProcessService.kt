package ru.nekoguys.game.web.service

import kotlinx.coroutines.flow.*
import org.springframework.stereotype.Service
import ru.nekoguys.game.entity.commongame.service.SessionPinDecoder
import ru.nekoguys.game.entity.competition.model.CompetitionPlayer
import ru.nekoguys.game.entity.competition.model.CompetitionSession
import ru.nekoguys.game.entity.competition.model.students
import ru.nekoguys.game.entity.competition.repository.CompetitionSessionRepository
import ru.nekoguys.game.entity.competition.repository.load
import ru.nekoguys.game.entity.competition.rule.CompetitionStageChangedMessage
import ru.nekoguys.game.web.dto.CompetitionInfoForStudentResultsTableResponse
import ru.nekoguys.game.web.dto.RoundEvent
import ru.nekoguys.game.entity.competition.CompetitionProcessService as CoreCompetitionProcessService

@Service("webCompetitionProcessService")
class CompetitionProcessService(
    // одноимённый класс уже есть в lib-game, здесь используется import alias
    private val coreCompetitionProcessService: CoreCompetitionProcessService,
    private val sessionPinDecoder: SessionPinDecoder,
    private val competitionSessionRepository: CompetitionSessionRepository,
) {

    fun competitionRoundEventsFlow(
        sessionPin: String,
    ): Flow<RoundEvent> = flow {
        val sessionId = sessionPinDecoder
            .decodeIdFromPin(sessionPin)
            ?: error("Incorrect pin")

        coreCompetitionProcessService
            .getAllMessagesForSession(sessionId)
            .map { it.body } // не смотрим, каким командам отправлено сообщение
            .transform { msg ->
                if (msg is CompetitionStageChangedMessage) {
                    emit(msg.toRoundEvent())
                }
            }
            .collect(::emit)
    }

    private fun CompetitionStageChangedMessage.toRoundEvent(): RoundEvent.EndRound =
        RoundEvent.EndRound(
            roundNumber = 0,
            isEndOfGame = false,
            roundLength = roundLength,
        )

    suspend fun getStudentCompInfo(
        studentEmail: String,
        sessionPin: String,
    ): CompetitionInfoForStudentResultsTableResponse? {
        val sessionId = sessionPinDecoder
            .decodeIdFromPin(sessionPin)
            ?: return null

        val session = competitionSessionRepository
            .load(
                sessionId,
                CompetitionSession.WithSettings,
                CompetitionSession.WithTeams,
            )

        val settings = session.settings

        val playerInfo = session
            .teams
            .asSequence()
            .flatMap { it.students }
            .find { it.user.email == studentEmail }
            ?: return null

        val team = session
            .teams
            .first { it.id == playerInfo.teamId }

        return CompetitionInfoForStudentResultsTableResponse(
            name = settings.name,
            description = settings.instruction,
            teamName = team.name,
            teamIdInGame = team.numberInGame,
            shouldShowResultTable = settings.showPreviousRoundResults,
            shouldShowResultTableInEnd = settings.showStudentsResultsTable,
            isCaptain = (playerInfo is CompetitionPlayer.TeamCaptain),
            roundsCount = settings.roundsCount,
            strategy = team.strategy.orEmpty(),
        )
    }
}
