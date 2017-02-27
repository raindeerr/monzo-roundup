package auth

import java.util.UUID

import play.api.Play

object MonzoAuth {

  val clientId: String = Play.current.configuration.getString("monzo.clientId").getOrElse("")

  val clientSecret: String = Play.current.configuration.getString("monzo.clientSecret").getOrElse("")

  val redirectBaseUri: String = Play.current.configuration.getString("monzo.redirectUri").getOrElse("http://localhost:9000")
  val redirectUri: String = redirectBaseUri + "/oauth/callback"

  def stateToken: String = UUID.randomUUID().toString

  val authUrl: String =
    s"""https://auth.getmondo.co.uk/?client_id=$clientId&redirect_uri=$redirectUri&response_type=code&state=$stateToken"""

}
