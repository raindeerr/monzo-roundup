import org.scalatest.TestData
import org.scalatestplus.play._
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test._
import play.api.test.Helpers._

class AuthControllerSpec extends PlaySpec with OneAppPerTest {

  val config = Map(
    "monzo.clientId" -> "testClientId",
    "monzo.clientSecret" -> "testClientSecret",
    "monzo.redirectUri" -> "http://redirectmehere.com"
  )

  implicit override def newAppForTest(testData: TestData): Application =
    new GuiceApplicationBuilder().configure(config).build()

  "AuthController" should {

    "not return NOT_FOUND" when {

      "the index page is accessed" in {
        route(app, FakeRequest(GET, "/")).map(status) mustBe Some(OK)
      }

    }

    "render the index page" in {
      val home = route(app, FakeRequest(GET, "/")).get

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Authorise")
    }

    "redirect to monzo for auth" in {
      val oauth = route(app, FakeRequest(GET, "/oauth/monzo")).get

      status(oauth) mustBe SEE_OTHER
      redirectLocation(oauth).get must include(
        "https://auth.getmondo.co.uk/?client_id=testClientId&redirect_uri=http://redirectmehere.com/oauth/callback&response_type=code&state="
      )
    }

  }


}
