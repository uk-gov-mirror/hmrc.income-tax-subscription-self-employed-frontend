/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Format, OFormat, OWrites, Reads, __}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption

case class NameAndTradeModel(
  id: String,
  name: String,
  trade: String,
  isAgent: Boolean
)

object NameAndTradeModel {
  def encryptedFormat(implicit crypto: Encrypter with Decrypter): OFormat[NameAndTradeModel] = {

    implicit val sensitiveFormat: Format[SensitiveString] = JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)

    val reads: Reads[NameAndTradeModel] = (
      (__ \ "id").read[String] and
        (__ \ "name").read[SensitiveString] and
        (__ \ "trade").read[String] and
        (__ \ "agent").read[Boolean]
      )((id, name, trade, isAgent) =>
        NameAndTradeModel.apply(
          id = id,
          name = name.decryptedValue,
          trade = trade,
          isAgent = isAgent
        )
    )

    val writes: OWrites[NameAndTradeModel] = (
      (__ \ "id").write[String] and
        (__ \ "name").write[SensitiveString] and
        (__ \ "trade").write[String] and
        (__ \ "agent").write[Boolean]
      )(nameAndTradeModel =>
        (
          nameAndTradeModel.id,
          SensitiveString.apply(nameAndTradeModel.name),
          nameAndTradeModel.trade,
          nameAndTradeModel.isAgent
        )
      )

      OFormat(reads, writes)
    }
 }