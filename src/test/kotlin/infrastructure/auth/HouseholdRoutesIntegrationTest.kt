package infrastructure.auth

import com.tenmilelabs.application.dto.AuthResponse
import com.tenmilelabs.application.dto.CreateHouseholdRequest
import com.tenmilelabs.application.dto.CreateInviteRequest
import com.tenmilelabs.application.dto.CreateInviteResponse
import com.tenmilelabs.application.dto.HouseholdResponse
import com.tenmilelabs.application.dto.InviteSummaryResponse
import com.tenmilelabs.application.dto.InvitePreviewResponse
import com.tenmilelabs.application.dto.JoinHouseholdRequest
import com.tenmilelabs.application.dto.MemberResponse
import com.tenmilelabs.application.dto.RegisterRequest
import com.tenmilelabs.application.dto.RenameHouseholdRequest
import com.tenmilelabs.application.service.module
import com.tenmilelabs.infrastructure.database.FakeHouseholdRepository
import com.tenmilelabs.infrastructure.database.FakeRecipesRepository
import com.tenmilelabs.infrastructure.database.FakeRefreshTokenRepository
import com.tenmilelabs.infrastructure.database.FakeSyncRepository
import com.tenmilelabs.infrastructure.database.FakeUserRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HouseholdRoutesIntegrationTest {

    private class Scope(val client: HttpClient)

    private fun testSetup(block: suspend Scope.() -> Unit) = testApplication {
        application {
            module(
                configureDatabase = false,
                recipeRepository = FakeRecipesRepository(),
                userRepository = FakeUserRepository(),
                refreshTokenRepository = FakeRefreshTokenRepository(),
                syncRepository = FakeSyncRepository(),
                householdRepository = FakeHouseholdRepository(),
            )
        }
        val client = createClient { install(ContentNegotiation) { json() } }
        Scope(client).block()
    }

    // ── POST /households ──────────────────────────────────────────────────────

    @Test
    fun `create household succeeds and makes the caller the owner`() = testSetup {
        val owner = client.registerAndGetAuth("owner1@example.com", "owner1")

        val response = client.post("/api/v1/households") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(CreateHouseholdRequest("The Muciente House"))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<HouseholdResponse>()
        assertEquals("The Muciente House", body.name)
        assertEquals(owner.userId, body.ownerId)
        assertEquals(1, body.members.size)
        assertEquals("OWNER", body.members.single().role)
    }

    @Test
    fun `create household with a blank name is a 400`() = testSetup {
        val owner = client.registerAndGetAuth("owner2@example.com", "owner2")

        val response = client.post("/api/v1/households") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(CreateHouseholdRequest("   "))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `create household while already in one is a 409`() = testSetup {
        val owner = client.registerAndGetAuth("owner3@example.com", "owner3")
        client.post("/api/v1/households") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(CreateHouseholdRequest("First"))
        }

        val response = client.post("/api/v1/households") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(CreateHouseholdRequest("Second"))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `create household without a bearer token is a 401`() = testSetup {
        val response = client.post("/api/v1/households") {
            contentType(ContentType.Application.Json)
            setBody(CreateHouseholdRequest("Household"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ── GET /households/me ────────────────────────────────────────────────────

    @Test
    fun `get my household returns 404 when the caller has none`() = testSetup {
        val user = client.registerAndGetAuth("nohousehold@example.com", "nohousehold")

        val response = client.get("/api/v1/households/me") { bearerAuth(user.token) }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `get my household returns it once created`() = testSetup {
        val owner = client.registerAndGetAuth("owner4@example.com", "owner4")
        client.post("/api/v1/households") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(CreateHouseholdRequest("Household"))
        }

        val response = client.get("/api/v1/households/me") { bearerAuth(owner.token) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Household", response.body<HouseholdResponse>().name)
    }

    // ── PATCH /households/{id} ────────────────────────────────────────────────

    @Test
    fun `owner can rename the household`() = testSetup {
        val owner = client.registerAndGetAuth("owner5@example.com", "owner5")
        val household = client.createHousehold(owner, "Old Name")

        val response = client.patch("/api/v1/households/${household.id}") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(RenameHouseholdRequest("New Name"))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("New Name", response.body<HouseholdResponse>().name)
    }

    @Test
    fun `a non-owner cannot rename the household`() = testSetup {
        val owner = client.registerAndGetAuth("owner6@example.com", "owner6")
        val household = client.createHousehold(owner, "Household")
        val member = client.joinViaFreshInvite(owner, household.id, "member6@example.com", "member6")

        val response = client.patch("/api/v1/households/${household.id}") {
            bearerAuth(member.token)
            contentType(ContentType.Application.Json)
            setBody(RenameHouseholdRequest("New Name"))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // ── DELETE /households/{id} ───────────────────────────────────────────────

    @Test
    fun `owner can delete the household`() = testSetup {
        val owner = client.registerAndGetAuth("owner7@example.com", "owner7")
        val household = client.createHousehold(owner, "Household")

        val response = client.delete("/api/v1/households/${household.id}") { bearerAuth(owner.token) }

        assertEquals(HttpStatusCode.NoContent, response.status)
        val after = client.get("/api/v1/households/me") { bearerAuth(owner.token) }
        assertEquals(HttpStatusCode.NotFound, after.status)
    }

    @Test
    fun `a non-owner cannot delete the household`() = testSetup {
        val owner = client.registerAndGetAuth("owner8@example.com", "owner8")
        val household = client.createHousehold(owner, "Household")
        val member = client.joinViaFreshInvite(owner, household.id, "member8@example.com", "member8")

        val response = client.delete("/api/v1/households/${household.id}") { bearerAuth(member.token) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // ── GET /households/{id}/members ──────────────────────────────────────────

    @Test
    fun `members list is visible to any active member`() = testSetup {
        val owner = client.registerAndGetAuth("owner9@example.com", "owner9")
        val household = client.createHousehold(owner, "Household")
        val member = client.joinViaFreshInvite(owner, household.id, "member9@example.com", "member9")

        val response = client.get("/api/v1/households/${household.id}/members") { bearerAuth(member.token) }

        assertEquals(HttpStatusCode.OK, response.status)
        val members = response.body<List<MemberResponse>>()
        assertEquals(2, members.size)
    }

    @Test
    fun `members list is forbidden to a non-member`() = testSetup {
        val owner = client.registerAndGetAuth("owner10@example.com", "owner10")
        val household = client.createHousehold(owner, "Household")
        val outsider = client.registerAndGetAuth("outsider10@example.com", "outsider10")

        val response = client.get("/api/v1/households/${household.id}/members") { bearerAuth(outsider.token) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // ── DELETE /households/{id}/members/{userId} ──────────────────────────────

    @Test
    fun `owner can remove a member`() = testSetup {
        val owner = client.registerAndGetAuth("owner11@example.com", "owner11")
        val household = client.createHousehold(owner, "Household")
        val member = client.joinViaFreshInvite(owner, household.id, "member11@example.com", "member11")

        val response = client.delete("/api/v1/households/${household.id}/members/${member.userId}") {
            bearerAuth(owner.token)
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
        val members = client.get("/api/v1/households/${household.id}/members") { bearerAuth(owner.token) }
            .body<List<MemberResponse>>()
        assertEquals(1, members.size)
    }

    @Test
    fun `owner removing themselves via the member-remove path is a 400`() = testSetup {
        val owner = client.registerAndGetAuth("owner12@example.com", "owner12")
        val household = client.createHousehold(owner, "Household")

        val response = client.delete("/api/v1/households/${household.id}/members/${owner.userId}") {
            bearerAuth(owner.token)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ── POST /households/{id}/members/me/leave ────────────────────────────────

    @Test
    fun `a member can leave the household`() = testSetup {
        val owner = client.registerAndGetAuth("owner13@example.com", "owner13")
        val household = client.createHousehold(owner, "Household")
        val member = client.joinViaFreshInvite(owner, household.id, "member13@example.com", "member13")

        val response = client.post("/api/v1/households/${household.id}/members/me/leave") {
            bearerAuth(member.token)
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
        val after = client.get("/api/v1/households/me") { bearerAuth(member.token) }
        assertEquals(HttpStatusCode.NotFound, after.status)
    }

    @Test
    fun `owner leaving alone dissolves the household`() = testSetup {
        val owner = client.registerAndGetAuth("owner14@example.com", "owner14")
        val household = client.createHousehold(owner, "Household")

        val response = client.post("/api/v1/households/${household.id}/members/me/leave") {
            bearerAuth(owner.token)
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
        val after = client.get("/api/v1/households/me") { bearerAuth(owner.token) }
        assertEquals(HttpStatusCode.NotFound, after.status)
    }

    // ── POST/GET/DELETE /households/{id}/invites ──────────────────────────────

    @Test
    fun `owner can create an invite and the raw token appears exactly in that response`() = testSetup {
        val owner = client.registerAndGetAuth("owner15@example.com", "owner15")
        val household = client.createHousehold(owner, "Household")

        val response = client.post("/api/v1/households/${household.id}/invites") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest())
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<CreateInviteResponse>()
        assertTrue(body.token.isNotBlank())
        assertTrue(body.url.endsWith("?token=${body.token}"))
    }

    @Test
    fun `a non-owner cannot create an invite`() = testSetup {
        val owner = client.registerAndGetAuth("owner16@example.com", "owner16")
        val household = client.createHousehold(owner, "Household")
        val member = client.joinViaFreshInvite(owner, household.id, "member16@example.com", "member16")

        val response = client.post("/api/v1/households/${household.id}/invites") {
            bearerAuth(member.token)
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest())
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `outstanding invites list never includes the raw token field`() = testSetup {
        val owner = client.registerAndGetAuth("owner17@example.com", "owner17")
        val household = client.createHousehold(owner, "Household")
        client.post("/api/v1/households/${household.id}/invites") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest())
        }

        val response = client.get("/api/v1/households/${household.id}/invites") { bearerAuth(owner.token) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, response.body<List<InviteSummaryResponse>>().size)

        val rawBody = client.get("/api/v1/households/${household.id}/invites") { bearerAuth(owner.token) }
            .bodyAsText()
        assertTrue(!rawBody.contains("\"token\""))
    }

    @Test
    fun `owner can revoke an outstanding invite`() = testSetup {
        val owner = client.registerAndGetAuth("owner18@example.com", "owner18")
        val household = client.createHousehold(owner, "Household")
        val invite = client.post("/api/v1/households/${household.id}/invites") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest())
        }.body<CreateInviteResponse>()
        val inviteId = client.get("/api/v1/households/${household.id}/invites") { bearerAuth(owner.token) }
            .body<List<InviteSummaryResponse>>().single().id

        val response = client.delete("/api/v1/households/${household.id}/invites/$inviteId") {
            bearerAuth(owner.token)
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
        val remaining = client.get("/api/v1/households/${household.id}/invites") { bearerAuth(owner.token) }
            .body<List<InviteSummaryResponse>>()
        assertTrue(remaining.isEmpty())

        // A revoked invite can no longer be joined via its token.
        val joinResponse = client.post("/api/v1/households/join") {
            bearerAuth(client.registerAndGetAuth("outsider18@example.com", "outsider18").token)
            contentType(ContentType.Application.Json)
            setBody(JoinHouseholdRequest(invite.token))
        }
        assertEquals(HttpStatusCode.NotFound, joinResponse.status)
    }

    // ── GET /households/invites/preview ───────────────────────────────────────

    @Test
    fun `preview works for a signed-out request`() = testSetup {
        val owner = client.registerAndGetAuth("owner19@example.com", "owner19")
        val household = client.createHousehold(owner, "The Muciente House")
        val invite = client.post("/api/v1/households/${household.id}/invites") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest())
        }.body<CreateInviteResponse>()

        val response = client.get("/api/v1/households/invites/preview?token=${invite.token}")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("The Muciente House", response.body<InvitePreviewResponse>().householdName)
    }

    @Test
    fun `preview with an unknown token is a 404 with the handler's JSON body`() = testSetup {
        val response = client.get("/api/v1/households/invites/preview?token=not-a-real-token")

        // Asserting the status alone isn't enough here: StatusPages' generic 404 fallback and
        // respondHouseholdException's own JSON 404 are indistinguishable by status code, and a
        // regression that makes the global fallback swallow this route's JSON body (as
        // `status(HttpStatusCode.NotFound) { }` used to, since it applies to any outgoing 404
        // rather than only a genuinely-unmatched route) would still pass a status-only check.
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertTrue(response.bodyAsText().contains("No invite found for the given token"))
    }

    @Test
    fun `a genuinely unmatched route is a 404 with the generic fallback body`() = testSetup {
        val response = client.get("/api/v1/this-route-does-not-exist")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("404: Resource Not Found", response.bodyAsText())
    }

    @Test
    fun `preview with a present but invalid bearer token is still a 401`() = testSetup {
        val response = client.get("/api/v1/households/invites/preview?token=whatever") {
            bearerAuth("not-a-real-jwt")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ── POST /households/join ─────────────────────────────────────────────────

    @Test
    fun `join by token succeeds and adds the caller as a member`() = testSetup {
        val owner = client.registerAndGetAuth("owner20@example.com", "owner20")
        val household = client.createHousehold(owner, "Household")
        val invite = client.post("/api/v1/households/${household.id}/invites") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest())
        }.body<CreateInviteResponse>()
        val joiner = client.registerAndGetAuth("joiner20@example.com", "joiner20")

        val response = client.post("/api/v1/households/join") {
            bearerAuth(joiner.token)
            contentType(ContentType.Application.Json)
            setBody(JoinHouseholdRequest(invite.token))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(household.id, response.body<HouseholdResponse>().id)
    }

    @Test
    fun `join without a bearer token is a 401`() = testSetup {
        val response = client.post("/api/v1/households/join") {
            contentType(ContentType.Application.Json)
            setBody(JoinHouseholdRequest("some-token"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `join while already in a household is a 409`() = testSetup {
        val owner = client.registerAndGetAuth("owner21@example.com", "owner21")
        val household = client.createHousehold(owner, "Household")
        val invite = client.post("/api/v1/households/${household.id}/invites") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest())
        }.body<CreateInviteResponse>()
        val alreadyMember = client.registerAndGetAuth("already21@example.com", "already21")
        client.createHousehold(alreadyMember, "Other Household")

        val response = client.post("/api/v1/households/join") {
            bearerAuth(alreadyMember.token)
            contentType(ContentType.Application.Json)
            setBody(JoinHouseholdRequest(invite.token))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    // ── GET /households/invites/pending ───────────────────────────────────────

    @Test
    fun `pending invites lists an email-addressed invite for the invitee`() = testSetup {
        val owner = client.registerAndGetAuth("owner22@example.com", "owner22")
        val household = client.createHousehold(owner, "Household")
        val invitee = client.registerAndGetAuth("invitee22@example.com", "invitee22")
        client.post("/api/v1/households/${household.id}/invites") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(inviteeEmail = "invitee22@example.com"))
        }

        val response = client.get("/api/v1/households/invites/pending") { bearerAuth(invitee.token) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, response.body<List<InviteSummaryResponse>>().size)
    }

    // ── POST /households/invites/{inviteId}/accept ────────────────────────────

    @Test
    fun `accept by invite id succeeds for the addressed invitee`() = testSetup {
        val owner = client.registerAndGetAuth("owner23@example.com", "owner23")
        val household = client.createHousehold(owner, "Household")
        val invitee = client.registerAndGetAuth("invitee23@example.com", "invitee23")
        client.post("/api/v1/households/${household.id}/invites") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(inviteeEmail = "invitee23@example.com"))
        }
        val inviteId = client.get("/api/v1/households/invites/pending") { bearerAuth(invitee.token) }
            .body<List<InviteSummaryResponse>>().single().id

        val response = client.post("/api/v1/households/invites/$inviteId/accept") { bearerAuth(invitee.token) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(household.id, response.body<HouseholdResponse>().id)
    }

    @Test
    fun `accept by invite id for a different account is a 403`() = testSetup {
        val owner = client.registerAndGetAuth("owner24@example.com", "owner24")
        val household = client.createHousehold(owner, "Household")
        val invitee = client.registerAndGetAuth("invitee24@example.com", "invitee24")
        client.post("/api/v1/households/${household.id}/invites") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(inviteeEmail = "invitee24@example.com"))
        }
        val inviteId = client.get("/api/v1/households/invites/pending") { bearerAuth(invitee.token) }
            .body<List<InviteSummaryResponse>>().single().id
        val impostor = client.registerAndGetAuth("impostor24@example.com", "impostor24")

        val response = client.post("/api/v1/households/invites/$inviteId/accept") { bearerAuth(impostor.token) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `accept by invite id refuses an open invite link`() = testSetup {
        val owner = client.registerAndGetAuth("owner24b@example.com", "owner24b")
        val household = client.createHousehold(owner, "Household")
        client.post("/api/v1/households/${household.id}/invites") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest())
        }
        val inviteId = client.get("/api/v1/households/${household.id}/invites") { bearerAuth(owner.token) }
            .body<List<InviteSummaryResponse>>().single().id
        val outsider = client.registerAndGetAuth("outsider24b@example.com", "outsider24b")

        val response = client.post("/api/v1/households/invites/$inviteId/accept") { bearerAuth(outsider.token) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `create invite with a malformed body is a 400, not a silently open invite`() = testSetup {
        val owner = client.registerAndGetAuth("owner24c@example.com", "owner24c")
        val household = client.createHousehold(owner, "Household")

        val malformed = client.post("/api/v1/households/${household.id}/invites") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody("""{"inviteeEmail":"someone@example.com","maxUses":{}}""")
        }
        val bodyless = client.post("/api/v1/households/${household.id}/invites") { bearerAuth(owner.token) }

        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        assertEquals(HttpStatusCode.Created, bodyless.status)
    }

    // ── POST /households/invites/{inviteId}/decline ───────────────────────────

    @Test
    fun `decline by the invitee removes it from the pending list without joining`() = testSetup {
        val owner = client.registerAndGetAuth("owner25@example.com", "owner25")
        val household = client.createHousehold(owner, "Household")
        val invitee = client.registerAndGetAuth("invitee25@example.com", "invitee25")
        client.post("/api/v1/households/${household.id}/invites") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(inviteeEmail = "invitee25@example.com"))
        }
        val inviteId = client.get("/api/v1/households/invites/pending") { bearerAuth(invitee.token) }
            .body<List<InviteSummaryResponse>>().single().id

        val response = client.post("/api/v1/households/invites/$inviteId/decline") { bearerAuth(invitee.token) }

        assertEquals(HttpStatusCode.NoContent, response.status)
        val pending = client.get("/api/v1/households/invites/pending") { bearerAuth(invitee.token) }
            .body<List<InviteSummaryResponse>>()
        assertTrue(pending.isEmpty())
        val afterDecline = client.get("/api/v1/households/me") { bearerAuth(invitee.token) }
        assertEquals(HttpStatusCode.NotFound, afterDecline.status)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private suspend fun HttpClient.registerAndGetAuth(email: String, username: String): AuthResponse {
        val response = post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = email, username = username, password = "TestPassword123!"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }

    private suspend fun HttpClient.createHousehold(owner: AuthResponse, name: String): HouseholdResponse {
        val response = post("/api/v1/households") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(CreateHouseholdRequest(name))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }

    /** Registers a fresh user, creates a single-use invite as [owner], and joins as that new user. */
    private suspend fun HttpClient.joinViaFreshInvite(
        owner: AuthResponse,
        householdId: String,
        joinerEmail: String,
        joinerUsername: String,
    ): AuthResponse {
        val invite = post("/api/v1/households/$householdId/invites") {
            bearerAuth(owner.token)
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest())
        }.body<CreateInviteResponse>()
        val joiner = registerAndGetAuth(joinerEmail, joinerUsername)
        val response = post("/api/v1/households/join") {
            bearerAuth(joiner.token)
            contentType(ContentType.Application.Json)
            setBody(JoinHouseholdRequest(invite.token))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return joiner
    }
}
