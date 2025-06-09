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

import java.util.UUID

class SoleTraderBusinessesSpec extends PlaySpec {

  private def business(name: String, trade: String) =
    SoleTraderBusiness(
      id = UUID.randomUUID().toString,
      name = Some(name),
      trade = Some(trade)
    )

  val noDuplicates = SoleTraderBusinesses(
    businesses = Seq(
      business("ABC Traders", "Plumbing"),
      business("Miko Ice Creams", "Food"),
      business("ABC Pipes", "Plumbing"),
      business("ABC Traders", "Electrician"),
      business("Plug It Well", "Electrician"),
    )
  )

  "SoleTraderBusinesses" should {
    "check duplicates correctly" when {
      "given no duplicates" in {
        noDuplicates.hasDuplicates mustBe false
      }
      "given a duplicate" in {
        val oneDuplicate = noDuplicates.copy(
          businesses = noDuplicates.businesses :+ noDuplicates.businesses.head.copy(
            id = UUID.randomUUID().toString
          )
        )
        oneDuplicate.hasDuplicates mustBe true
      }
      "given more than ome duplicate" in {
        val withDuplicates = noDuplicates.copy(
          businesses = noDuplicates.businesses ++ noDuplicates.businesses.map(_.copy(
            id = UUID.randomUUID().toString
          ))
        )
        withDuplicates.hasDuplicates mustBe true
      }
    }
  }
}
