package ru.nekoguys.game.entity.competition.rule

import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Component
import ru.nekoguys.game.core.GameMessage
import ru.nekoguys.game.core.util.buildResponse
import ru.nekoguys.game.entity.competition.competitionProcessError
import ru.nekoguys.game.entity.competition.model.*
import ru.nekoguys.game.entity.competition.repository.CompetitionSessionRepository
import ru.nekoguys.game.entity.competition.repository.CompetitionTeamRepository
import ru.nekoguys.game.entity.competition.repository.load
import ru.nekoguys.game.entity.user.repository.UserRepository

data class CompetitionJoinTeamMessage(
    val teamName: String,
    val idInGame: Int,
    val membersEmails: List<String>,
) : CompetitionMessage()

@Component
class CompetitionJoinTeamRule(
    private val userRepository: UserRepository,
    private val competitionSessionRepository: CompetitionSessionRepository,
    private val competitionTeamRepository: CompetitionTeamRepository,
) : CompetitionRule<CompetitionPlayer.Unknown, CompetitionCommand.JoinTeam, CompetitionJoinTeamMessage> {

    override suspend fun process(
        player: CompetitionPlayer.Unknown,
        command: CompetitionCommand.JoinTeam,
    ): List<CompGameMessage<CompetitionJoinTeamMessage>> {
        val session = competitionSessionRepository
            .load(
                player.sessionId,
                CompetitionSession.WithSettings,
                CompetitionSession.WithTeams,
                CompetitionSession.WithStage,
            )

        val oldTeam = findTeamToUpdate(command, session.teams)

        validateCommand(
            command = command,
            oldTeam = oldTeam,
            maxTeamSize = session.settings.maxTeamSize,
            currentStage = session.stage,
        )

        val newTeam = doUpdate(player, oldTeam)

        return createResponse(command, newTeam)
    }

    private fun findTeamToUpdate(
        command: CompetitionCommand.JoinTeam,
        teams: List<CompetitionTeam>,
    ): CompetitionTeam =
        teams
            .singleOrNull { it.name == command.teamName }
            ?: competitionProcessError("No team in competition with name: ${command.teamName}")

    private fun validateCommand(
        command: CompetitionCommand.JoinTeam,
        oldTeam: CompetitionTeam,
        maxTeamSize: Int,
        currentStage: CompetitionStage,
    ) {
        if (command.password != oldTeam.password) {
            competitionProcessError("Wrong team password")
        }

        if (oldTeam.teamMembers.size > maxTeamSize) {
            competitionProcessError(
                "There are too much team members already, max amount: $maxTeamSize"
            )
        }

        if (currentStage != CompetitionStage.Registration) {
            competitionProcessError("Illegal competition state")
        }
    }

    private suspend fun doUpdate(
        player: CompetitionPlayer.Unknown,
        oldTeam: CompetitionTeam
    ): CompetitionTeam {
        val newMember = CompetitionPlayer.TeamMember(
            sessionId = player.sessionId,
            user = player.user,
            teamId = oldTeam.id,
        )
        val newTeam = oldTeam.copy(
            teamMembers = oldTeam.teamMembers + newMember
        )
        competitionTeamRepository.update(from = oldTeam, to = newTeam)
        return newTeam
    }

    private suspend fun createResponse(
        command: CompetitionCommand.JoinTeam,
        team: CompetitionTeam,
    ): List<GameMessage<CompetitionTeam.Id, CompetitionJoinTeamMessage>> {
        val memberEmails = userRepository
            .findAll(team.students.map { it.user.id.number })
            .toList()
            .map { it.email }

        return buildResponse {
            team.id {
                +CompetitionJoinTeamMessage(
                    teamName = command.teamName,
                    idInGame = team.numberInGame,
                    membersEmails = memberEmails,
                )
            }
        }
    }
}
