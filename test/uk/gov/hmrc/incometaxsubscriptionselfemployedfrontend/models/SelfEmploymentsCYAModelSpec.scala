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

import org.scalatestplus.play.PlaySpec

class SelfEmploymentsCYAModelSpec extends PlaySpec {

  val dateModel: DateModel = DateModel("1", "2", "1980")

  val address: Address = Address(
    lines = Seq("1 long road"),
    postcode = Some("ZZ1 1ZZ")
  )

  val fullModel: SelfEmploymentsCYAModel = SelfEmploymentsCYAModel(
    id = "test-id",
    confirmed = true,
    startDateBeforeLimit = Some(false),
    businessStartDate = Some(dateModel),
    businessName = Some("test name"),
    businessTradeName = Some("test trade"),
    businessAddress = Some(address),
    accountingMethod = Some(Cash),
    totalSelfEmployments = 1,
    isFirstBusiness = true
  )

  "SelfEmploymentsCYAModel.isComplete" must {

    "when removeAccountingMethod is true" should {
      "return true" when {
        "start date before limit is defined and true" in {
          fullModel.copy(startDateBeforeLimit = Some(true), businessStartDate = None).isComplete(removeAccountingMethod = true) mustBe true
        }
        "start date before limit is defined and false and start date defined" in {
          fullModel.copy(startDateBeforeLimit = Some(false)).isComplete(removeAccountingMethod = true) mustBe true
        }
        "start date before limit is not defined and start date defined" in {
          fullModel.copy(startDateBeforeLimit = None).isComplete(removeAccountingMethod = true) mustBe true
        }
      }

      "return false" when {
        "start date before limit is defined and true and name is not defined" in {
          fullModel.copy(startDateBeforeLimit = Some(true), businessStartDate = None, businessName = None).isComplete(removeAccountingMethod = true) mustBe false
        }
        "start date before limit is defined and true and trade is not defined" in {
          fullModel.copy(startDateBeforeLimit = Some(true), businessStartDate = None, businessTradeName = None).isComplete(removeAccountingMethod = true) mustBe false
        }
        "start date before limit is defined and true and address is not defined" in {
          fullModel.copy(startDateBeforeLimit = Some(true), businessStartDate = None, businessAddress = None).isComplete(removeAccountingMethod = true) mustBe false
        }
        "start date before limit is defined and false and start date is not defined" in {
          fullModel.copy(startDateBeforeLimit = Some(false), businessStartDate = None).isComplete(removeAccountingMethod = true) mustBe false
        }
        "start date before limit is not defined and start date is not defined" in {
          fullModel.copy(startDateBeforeLimit = None, businessStartDate = None).isComplete(removeAccountingMethod = true) mustBe false
        }
      }
    }

    "when removeAccountingMethod is false" should {
      "return true" when {
        "start date before limit is defined and true and all fields are defined" in {
          fullModel.copy(startDateBeforeLimit = Some(true), businessStartDate = None).isComplete(removeAccountingMethod = false) mustBe true
        }
        "start date before limit is defined and false and all fields are defined" in {
          fullModel.isComplete(removeAccountingMethod = false) mustBe true
        }
        "start date before limit is not defined and all fields are defined" in {
          fullModel.copy(startDateBeforeLimit = None).isComplete(removeAccountingMethod = false) mustBe true
        }
      }

      "return false" when {
        "start date before limit is defined and true and accounting method is not defined" in {
          fullModel.copy(startDateBeforeLimit = Some(true), businessStartDate = None, accountingMethod = None).isComplete(removeAccountingMethod = false) mustBe false
        }
        "start date before limit is defined and false and start date is not defined" in {
          fullModel.copy(businessStartDate = None, accountingMethod = None).isComplete(removeAccountingMethod = false) mustBe false
        }
        "name is not defined" in {
          fullModel.copy(businessName = None, accountingMethod = None).isComplete(removeAccountingMethod = false) mustBe false
        }
        "trade is not defined" in {
          fullModel.copy(businessTradeName = None, accountingMethod = None).isComplete(removeAccountingMethod = false) mustBe false
        }
        "address is not defined" in {
          fullModel.copy(businessAddress = None, accountingMethod = None).isComplete(removeAccountingMethod = false) mustBe false
        }
        "accounting method is not defined" in {
          fullModel.copy(accountingMethod = None).isComplete(removeAccountingMethod = false) mustBe false
        }
      }
    }
  }
}
