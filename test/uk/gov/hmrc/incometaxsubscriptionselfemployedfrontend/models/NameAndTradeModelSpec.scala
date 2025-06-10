/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models

import org.scalatestplus.play.PlaySpec
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Environment, Mode}
import play.api.libs.json.{JsSuccess, Json, OFormat}
import uk.gov.hmrc.crypto.{ApplicationCrypto, Decrypter, Encrypter, SymmetricCryptoFactory}
import uk.gov.hmrc.http.HeaderCarrier.Config

import java.util.UUID

class NameAndTradeModelSpec extends PlaySpec {

  val mockPort = "11111"
  val mockHost = "localhost"
  val mockUrl: String = s"http://$mockHost:$mockPort"

  def random: String =
    UUID.randomUUID().toString

  val model = NameAndTradeModel(
    reference = random,
    id = random,
    name = random,
    trade = random,
    isAgent = random.contains("A")
  )

  val KEY = "json.encryption.key"

  val config = Map(
    KEY -> "AKxuJP8pVtMSlMImimoeTYoxxG0HUMOlh7BxiQkrkW8="
  )

  implicit lazy val crypto: Encrypter with Decrypter = SymmetricCryptoFactory.aesCrypto(config.getOrElse(KEY, throw new Exception()))
  implicit lazy val format: OFormat[NameAndTradeModel] = NameAndTradeModel.encryptedFormat

  "NameAndTradeModel" should {
    "read/write Json correctly" in {
      val json = Json.toJson(model)
      Json.fromJson(json) mustBe JsSuccess(model)
    }
  }
}
