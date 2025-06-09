/*
 * Copyright 2024 HM Revenue & Customs
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
import play.api.libs.json._
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}

case class SoleTraderBusiness(id: String,
                              confirmed: Boolean = false,
                              startDateBeforeLimit: Option[Boolean] = None,
                              startDate: Option[DateModel] = None,
                              name: Option[String] = None,
                              trade: Option[String] = None,
                              address: Option[Address] = None) {

  private def isSameAs(other: SoleTraderBusiness): Boolean = {
    if (id != other.id) {
      val thisName = name.getOrElse("")
      val otherName = other.name.getOrElse("")
      if (thisName == otherName) {
        val thisTrade = trade.getOrElse("")
        val otherTrade = other.trade.getOrElse("")
        thisTrade == otherTrade
      } else {
        false
      }
    } else {
      false
    }
  }

  def isDuplicate(others: Seq[SoleTraderBusiness]): Boolean =
    others.exists(isSameAs)
}

object SoleTraderBusiness {

  def encryptedFormat(implicit crypto: Encrypter with Decrypter): OFormat[SoleTraderBusiness] = {

    implicit val sensitiveFormat: Format[SensitiveString] = JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)

    implicit val addressFormat: OFormat[Address] = Address.encryptedFormat

    val reads: Reads[SoleTraderBusiness] = (
      (__ \ "id").read[String] and
        (__ \ "confirmed").read[Boolean] and
        (__ \ "startDateBeforeLimit").readNullable[Boolean] and
        (__ \ "startDate").readNullable[DateModel] and
        (__ \ "name").readNullable[SensitiveString] and
        (__ \ "trade").readNullable[String] and
        (__ \ "address").readNullable[Address]
      )(
      (id, confirmed, startDateBeforeLimit, startDate, name, trade, address) =>
        SoleTraderBusiness.apply(id, confirmed, startDateBeforeLimit, startDate, name.map(_.decryptedValue), trade, address)
    )

    val writes: OWrites[SoleTraderBusiness] = (
      (__ \ "id").write[String] and
        (__ \ "confirmed").write[Boolean] and
        (__ \ "startDateBeforeLimit").writeNullable[Boolean] and
        (__ \ "startDate").writeNullable[DateModel] and
        (__ \ "name").writeNullable[SensitiveString] and
        (__ \ "trade").writeNullable[String] and
        (__ \ "address").writeNullable[Address]
      )(
      soleTraderBusiness =>
        (
          soleTraderBusiness.id,
          soleTraderBusiness.confirmed,
          soleTraderBusiness.startDateBeforeLimit,
          soleTraderBusiness.startDate,
          soleTraderBusiness.name.map(SensitiveString.apply),
          soleTraderBusiness.trade,
          soleTraderBusiness.address
        )
    )

    OFormat(reads, writes)
  }

}

