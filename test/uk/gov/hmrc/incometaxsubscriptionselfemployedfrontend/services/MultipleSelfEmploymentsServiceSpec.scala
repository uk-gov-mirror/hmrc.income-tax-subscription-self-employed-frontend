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

package uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.services

import org.scalatestplus.play.PlaySpec
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.SelfEmploymentDataKeys.{incomeSourcesComplete, soleTraderBusinessesKey}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.connectors.httpparser.{DeleteSubscriptionDetailsHttpParser, GetSelfEmploymentsHttpParser, PostSelfEmploymentsHttpParser}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.connectors.mocks.MockIncomeTaxSubscriptionConnector
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models._
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.agent.StreamlineBusiness

import java.util.UUID

class MultipleSelfEmploymentsServiceSpec extends PlaySpec with MockIncomeTaxSubscriptionConnector {

  val applicationCrypto: ApplicationCrypto = app.injector.instanceOf[ApplicationCrypto]

  trait Setup {

    val service: MultipleSelfEmploymentsService = new MultipleSelfEmploymentsService(
      applicationCrypto = applicationCrypto,
      incomeTaxSubscriptionConnector = mockIncomeTaxSubscriptionConnector
    )

  }

  val testReference: String = "test-reference"

  val id: String = "id"
  val date: DateModel = DateModel("1", "1", "1980")
  val name: String = "test name"
  val trade: String = "test trade"
  val address: Address = Address(
    lines = Seq("1 Long Road", "Lonely Town"),
    postcode = Some("ZZ1 1ZZ")
  )
  val accountingMethod: AccountingMethod = Cash

  val soleTraderBusiness: SoleTraderBusiness = SoleTraderBusiness(
    id = id,
    startDateBeforeLimit = Some(false),
    startDate = Some(date),
    name = Some(name),
    trade = Some(trade),
    address = Some(address)
  )

  def soleTraderBusinessTwo(
                             startDate: Option[DateModel] = None,
                             startDateBeforeLimit: Option[Boolean] = None,
                             name: Option[String] = None,
                             trade: Option[String] = None,
                             address: Option[Address] = None
                           ): SoleTraderBusiness = SoleTraderBusiness(
    id = s"$id-2",
    startDate = startDate,
    startDateBeforeLimit = startDateBeforeLimit,
    name = name,
    trade = trade,
    address = address
  )

  val soleTraderBusinesses: SoleTraderBusinesses = SoleTraderBusinesses(
    businesses = Seq(soleTraderBusiness),
    accountingMethod = Some(accountingMethod)
  )

  def multipleSoleTraderBusinesses(
                                    otherBusiness: SoleTraderBusiness = soleTraderBusinessTwo()
                                  ): SoleTraderBusinesses = SoleTraderBusinesses(
    businesses = Seq(soleTraderBusiness, otherBusiness),
    accountingMethod = Some(accountingMethod)
  )

  "fetchSoleTraderBusinesses" must {
    "return sole trader businesses" when {
      "the connector returns sole trader businesses" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(soleTraderBusinesses))
        )

        await(service.fetchSoleTraderBusinesses(testReference)) mustBe Right(Some(soleTraderBusinesses))
      }
    }
    "return no sole trader businesses" when {
      "the connector returns no data" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(None)
        )

        await(service.fetchSoleTraderBusinesses(testReference)) mustBe Right(None)
      }
    }
    "return an error" when {
      "the connector returns an error" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Left(GetSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.fetchSoleTraderBusinesses(testReference)) mustBe
          Left(GetSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
      }
    }
  }

  "saveSoleTraderBusinesses" must {
    "return a save successful response" when {
      "the sole trader businesses was saved successfully and the income source confirmation was deleted" in new Setup {
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, soleTraderBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveSoleTraderBusinesses(testReference, soleTraderBusinesses)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
    }
    "return a save failure" when {
      "there was a problem when saving the sole trader businesses" in new Setup {
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, soleTraderBusinesses)(
          Left(PostSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.saveSoleTraderBusinesses(testReference, soleTraderBusinesses)) mustBe
          Left(MultipleSelfEmploymentsService.SaveSelfEmploymentDataFailure)
      }
      "there was a problem when deleting the income source completion field" in new Setup {
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, soleTraderBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Left(DeleteSubscriptionDetailsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.saveSoleTraderBusinesses(testReference, soleTraderBusinesses)) mustBe
          Left(MultipleSelfEmploymentsService.SaveSelfEmploymentDataFailure)
      }
    }
    "return a duplicate error" when {
      "there are duplicates in the data to save" in new Setup {
        val soleTraderBusinessesWithDuplicates = soleTraderBusinesses.copy(
          businesses = soleTraderBusinesses.businesses ++ soleTraderBusinesses.businesses.map(_.copy(
            id = UUID.randomUUID().toString
          ))
        )
        await(service.saveSoleTraderBusinesses(testReference, soleTraderBusinessesWithDuplicates)) mustBe
          Left(MultipleSelfEmploymentsService.SaveSelfEmploymentDataDuplicates)
      }
    }
  }

  "fetchStartDate" must {
    "return a start data" when {
      "there are multiple businesses" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(multipleSoleTraderBusinesses()))
        )

        await(service.fetchStartDate(testReference, id)) mustBe Right(Some(date))
      }
      "the business specified exists and has a start date" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(soleTraderBusinesses))
        )

        await(service.fetchStartDate(testReference, id)) mustBe Right(Some(date))
      }
    }
    "return no start date" when {
      "the business specified exists but has no start date" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(startDate = None)))))
        )

        await(service.fetchStartDate(testReference, id)) mustBe Right(None)
      }
      "the business specified does not exist" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(id = "other-id")))))
        )

        await(service.fetchStartDate(testReference, id)) mustBe Right(None)
      }
      "there are no businesses" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(soleTraderBusinesses.copy(businesses = Seq.empty[SoleTraderBusiness])))
        )

        await(service.fetchStartDate(testReference, id)) mustBe Right(None)
      }
    }
    "return an error" when {
      "an error was returned when retrieving sole trader businesses" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Left(GetSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.fetchStartDate(testReference, id)) mustBe
          Left(GetSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
      }
    }
  }

  "saveStartDate" must {
    "return a save successful response" when {
      "there was an already existing business which had its start date updated" in new Setup {
        val saveData: DateModel = DateModel("2", "2", "1980")
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(confirmed = true)))
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(startDate = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveStartDate(testReference, id, saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
      "there was an already existing business which had its start date added" in new Setup {
        val saveData: DateModel = DateModel("2", "2", "1980")
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(startDate = None)))
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(startDate = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveStartDate(testReference, id, saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
      "there is an already existing business which does not match the saved data id" in new Setup {
        val saveData: DateModel = DateModel("2", "2", "1980")
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses
        val newBusinesses: SoleTraderBusinesses = multipleSoleTraderBusinesses(soleTraderBusinessTwo(startDate = Some(saveData)))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveStartDate(testReference, s"$id-2", saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
      "there was no existing business matching the id, one was created with the id and start date" in new Setup {
        val saveData: DateModel = DateModel("2", "2", "1980")
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq.empty[SoleTraderBusiness])
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(SoleTraderBusiness(id, startDate = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveStartDate(testReference, id, saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
      "no sole trader businesses were returned" in new Setup {
        val saveData: DateModel = DateModel("2", "2", "1980")
        val newBusinesses: SoleTraderBusinesses = SoleTraderBusinesses(businesses = Seq(SoleTraderBusiness(id, startDate = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(None)
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveStartDate(testReference, id, saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
    }
    "return an error" when {
      "there was an error fetching the sole trader businesses" in new Setup {
        val saveData: DateModel = DateModel("2", "2", "1980")

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Left(GetSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.saveStartDate(testReference, id, saveData)) mustBe
          Left(MultipleSelfEmploymentsService.SaveSelfEmploymentDataFailure)
      }
      "there was an error saving the sole trader businesses" in new Setup {
        val saveData: DateModel = DateModel("2", "2", "1980")
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(startDate = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Left(PostSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.saveStartDate(testReference, id, saveData)) mustBe
          Left(MultipleSelfEmploymentsService.SaveSelfEmploymentDataFailure)
      }
      "there was an error when deleting the income source completion field" in new Setup {
        val saveData: DateModel = DateModel("2", "2", "1980")
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(startDate = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Left(DeleteSubscriptionDetailsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.saveStartDate(testReference, id, saveData)) mustBe
          Left(MultipleSelfEmploymentsService.SaveSelfEmploymentDataFailure)
      }
    }
  }

  "saveName" must {
    "return a save successful response" when {
      "there was an already existing business which had its start date updated" in new Setup {
        val saveData: String = "test other name"
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(confirmed = true)))
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(name = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveName(testReference, id, saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
      "there was an already existing business which had its start date added" in new Setup {
        val saveData: String = "test other name"
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(name = None)))
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(name = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveName(testReference, id, saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
      "there is an already existing business which does not match the saved data id" in new Setup {
        val saveData: String = "test other name"
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses
        val newBusinesses: SoleTraderBusinesses = multipleSoleTraderBusinesses(soleTraderBusinessTwo(name = Some(saveData)))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveName(testReference, s"$id-2", saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
      "there was no existing business matching the id, one was created with the id and start date" in new Setup {
        val saveData: String = "test other name"
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq.empty[SoleTraderBusiness])
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(SoleTraderBusiness(id, name = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveName(testReference, id, saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
      "no sole trader businesses were returned" in new Setup {
        val saveData: String = "test other name"
        val newBusinesses: SoleTraderBusinesses = SoleTraderBusinesses(businesses = Seq(SoleTraderBusiness(id, name = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(None)
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveName(testReference, id, saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
    }
    "return an error" when {
      "there was an error fetching the sole trader businesses" in new Setup {
        val saveData: String = "test other name"

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Left(GetSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.saveName(testReference, id, saveData)) mustBe
          Left(MultipleSelfEmploymentsService.SaveSelfEmploymentDataFailure)
      }
      "there was an error saving the sole trader businesses" in new Setup {
        val saveData: String = "test other name"
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(name = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Left(PostSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.saveName(testReference, id, saveData)) mustBe
          Left(MultipleSelfEmploymentsService.SaveSelfEmploymentDataFailure)
      }
    }
  }

  "saveTrade" must {
    "return a save successful response" when {
      "there was an already existing business which had its start date updated" in new Setup {
        val saveData: String = "test other trade"
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(confirmed = true)))
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(trade = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveTrade(testReference, id, saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
      "there was an already existing business which had its start date added" in new Setup {
        val saveData: String = "test other trade"
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(trade = None)))
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(trade = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveTrade(testReference, id, saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
      "there is an already existing business which does not match the saved data id" in new Setup {
        val saveData: String = "test other trade"
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses
        val newBusinesses: SoleTraderBusinesses = multipleSoleTraderBusinesses(soleTraderBusinessTwo(trade = Some(saveData)))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveTrade(testReference, s"$id-2", saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
      "there was no existing business matching the id, one was created with the id and start date" in new Setup {
        val saveData: String = "test other trade"
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq.empty[SoleTraderBusiness])
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(SoleTraderBusiness(id, trade = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveTrade(testReference, id, saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
      "no sole trader businesses were returned" in new Setup {
        val saveData: String = "test other trade"
        val newBusinesses: SoleTraderBusinesses = SoleTraderBusinesses(businesses = Seq(SoleTraderBusiness(id, trade = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(None)
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveTrade(testReference, id, saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
    }
    "return an error" when {
      "there was an error fetching the sole trader businesses" in new Setup {
        val saveData: String = "test other trade"

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Left(GetSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.saveTrade(testReference, id, saveData)) mustBe
          Left(MultipleSelfEmploymentsService.SaveSelfEmploymentDataFailure)
      }
      "there was an error saving the sole trader businesses" in new Setup {
        val saveData: String = "test other trade"
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(trade = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Left(PostSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.saveTrade(testReference, id, saveData)) mustBe
          Left(MultipleSelfEmploymentsService.SaveSelfEmploymentDataFailure)
      }
      "there was an error deleting the income source completed field" in new Setup {
        val saveData: String = "test other trade"
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(trade = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Left(DeleteSubscriptionDetailsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.saveTrade(testReference, id, saveData)) mustBe
          Left(MultipleSelfEmploymentsService.SaveSelfEmploymentDataFailure)
      }
    }
  }

  "saveAddress" must {
    "return a save successful response" when {
      "there was an already existing business which had its start date updated" in new Setup {
        val saveData: Address = Address(lines = Seq("2 Big Street"), postcode = Some("ZZ2 2ZZ"))
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(confirmed = true)))
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(address = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveAddress(testReference, id, saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
      "there was an already existing business which had its start date added" in new Setup {
        val saveData: Address = Address(lines = Seq("2 Big Street"), postcode = Some("ZZ2 2ZZ"))
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(address = None)))
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(address = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveAddress(testReference, id, saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
      "there is an already existing business which does not match the saved data id" in new Setup {
        val saveData: Address = Address(lines = Seq("2 Big Street"), postcode = Some("ZZ2 2ZZ"))
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses
        val newBusinesses: SoleTraderBusinesses = multipleSoleTraderBusinesses(soleTraderBusinessTwo(address = Some(saveData)))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveAddress(testReference, s"$id-2", saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
      "there was no existing business matching the id, one was created with the id and start date" in new Setup {
        val saveData: Address = Address(lines = Seq("2 Big Street"), postcode = Some("ZZ2 2ZZ"))
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq.empty[SoleTraderBusiness])
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(SoleTraderBusiness(id, address = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveAddress(testReference, id, saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
      "no sole trader businesses were returned" in new Setup {
        val saveData: Address = Address(lines = Seq("2 Big Street"), postcode = Some("ZZ2 2ZZ"))
        val newBusinesses: SoleTraderBusinesses = SoleTraderBusinesses(businesses = Seq(SoleTraderBusiness(id, address = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(None)
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveAddress(testReference, id, saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
    }
    "return an error" when {
      "there was an error fetching the sole trader businesses" in new Setup {
        val saveData: Address = Address(lines = Seq("2 Big Street"), postcode = Some("ZZ2 2ZZ"))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Left(GetSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.saveAddress(testReference, id, saveData)) mustBe
          Left(MultipleSelfEmploymentsService.SaveSelfEmploymentDataFailure)
      }
      "there was an error saving the sole trader businesses" in new Setup {
        val saveData: Address = Address(lines = Seq("2 Big Street"), postcode = Some("ZZ2 2ZZ"))
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(address = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Left(PostSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.saveAddress(testReference, id, saveData)) mustBe
          Left(MultipleSelfEmploymentsService.SaveSelfEmploymentDataFailure)
      }
      "there was an error deleting the income source completed field" in new Setup {
        val saveData: Address = Address(lines = Seq("2 Big Street"), postcode = Some("ZZ2 2ZZ"))
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(address = Some(saveData))))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Left(PostSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Left(DeleteSubscriptionDetailsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.saveAddress(testReference, id, saveData)) mustBe
          Left(MultipleSelfEmploymentsService.SaveSelfEmploymentDataFailure)
      }
    }
  }

  "confirmBusiness" must {
    "return a save successful response" when {
      "there was an already existing business which was already confirmed" in new Setup {
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(confirmed = true)))
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(confirmed = true)))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.confirmBusiness(testReference, id)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
      "there was an already existing business which was not confirmed" in new Setup {
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness))
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(confirmed = true)))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.confirmBusiness(testReference, id)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
    }
    "return an error" when {
      "there was an error fetching the sole trader businesses" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Left(GetSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.confirmBusiness(testReference, id)) mustBe
          Left(MultipleSelfEmploymentsService.SaveSelfEmploymentDataFailure)
      }
      "there was an error saving the sole trader businesses" in new Setup {
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(confirmed = true)))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Left(PostSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.confirmBusiness(testReference, id)) mustBe
          Left(MultipleSelfEmploymentsService.SaveSelfEmploymentDataFailure)
      }
      "there was an error deleting the income source completed field" in new Setup {
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(confirmed = true)))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Left(DeleteSubscriptionDetailsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.confirmBusiness(testReference, id)) mustBe
          Left(MultipleSelfEmploymentsService.SaveSelfEmploymentDataFailure)
      }
    }
  }

  "fetchAccountingMethod" must {
    "return an accounting method" when {
      "there are sole trader businesses and the accounting method exists" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(soleTraderBusinesses))
        )

        await(service.fetchAccountingMethod(testReference)) mustBe Right(Some(accountingMethod))
      }
    }
    "return no accounting method" when {
      "there are sole trader businesses but no accounting method set" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(soleTraderBusinesses.copy(accountingMethod = None)))
        )

        await(service.fetchAccountingMethod(testReference)) mustBe Right(None)
      }
      "there are no sole trader businesses" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(None)
        )

        await(service.fetchAccountingMethod(testReference)) mustBe Right(None)
      }
    }
    "return an error" when {
      "an error was returned when retrieving sole trader businesses" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Left(GetSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.fetchAccountingMethod(testReference)) mustBe
          Left(GetSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
      }
    }
  }

  "saveAccountingMethod" must {
    "return a save successful response" when {
      "there are already existing sole trader businesses with an accounting method" in new Setup {
        val saveData: AccountingMethod = Accruals
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(accountingMethod = Some(saveData))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveAccountingMethod(testReference, id, saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
      "there are already existing sole trader businesses without an accounting method" in new Setup {
        val saveData: AccountingMethod = Accruals
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(accountingMethod = Some(saveData))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveAccountingMethod(testReference, id, saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
      "there are already existing sole trader businesses with an updated accounting method" in new Setup {
        val saveData: AccountingMethod = Accruals

        val business1: SoleTraderBusiness = soleTraderBusiness.copy(id = id, confirmed = true, name = Some("A"))
        val business2: SoleTraderBusiness = soleTraderBusiness.copy(id = s"$id-2", confirmed = true, name = Some("B"))
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(businesses = Seq(business1, business2))

        val updatedBusiness1: SoleTraderBusiness = business1.copy(confirmed = false)
        val updatedBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(
          businesses = Seq(updatedBusiness1, business2),
          accountingMethod = Some(saveData)
        )

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )

        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, updatedBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )

        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveAccountingMethod(testReference, id, saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)

      }
      "no sole trader businesses were returned" in new Setup {
        val saveData: AccountingMethod = Accruals
        val newBusinesses: SoleTraderBusinesses = SoleTraderBusinesses(businesses = Seq.empty[SoleTraderBusiness], accountingMethod = Some(saveData))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(None)
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Right(DeleteSubscriptionDetailsHttpParser.DeleteSubscriptionDetailsSuccessResponse)
        )

        await(service.saveAccountingMethod(testReference, id, saveData)) mustBe
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
      }
    }
    "return an error" when {
      "there was an error fetching the sole trader businesses" in new Setup {
        val saveData: AccountingMethod = Accruals

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Left(GetSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.saveAccountingMethod(testReference, id, saveData)) mustBe
          Left(MultipleSelfEmploymentsService.SaveSelfEmploymentDataFailure)
      }
      "there was an error saving the sole trader businesses" in new Setup {
        val saveData: AccountingMethod = Accruals
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(accountingMethod = Some(saveData))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Left(PostSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.saveAccountingMethod(testReference, id, saveData)) mustBe
          Left(MultipleSelfEmploymentsService.SaveSelfEmploymentDataFailure)
      }
      "there was an error deleting the income source completed field" in new Setup {
        val saveData: AccountingMethod = Accruals
        val oldBusinesses: SoleTraderBusinesses = soleTraderBusinesses
        val newBusinesses: SoleTraderBusinesses = soleTraderBusinesses.copy(accountingMethod = Some(saveData))

        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(oldBusinesses))
        )
        mockSaveSubscriptionDetails(testReference, soleTraderBusinessesKey, newBusinesses)(
          Right(PostSelfEmploymentsHttpParser.PostSubscriptionDetailsSuccessResponse)
        )
        mockDeleteSubscriptionDetails(testReference, incomeSourcesComplete)(
          Left(DeleteSubscriptionDetailsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.saveAccountingMethod(testReference, id, saveData)) mustBe
          Left(MultipleSelfEmploymentsService.SaveSelfEmploymentDataFailure)
      }
    }
  }

  "fetchFirstAddress" must {
    "return an address" when {
      "an address already exists" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(Right(Some(soleTraderBusinesses)))

        await(service.fetchFirstAddress(testReference)) mustBe Right(Some(address))
      }
    }
    "return no address" when {
      "there are multiple businesses without addresses" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(address = None), soleTraderBusinessTwo()))))
        )

        await(service.fetchFirstAddress(testReference)) mustBe Right(None)
      }
      "there is a single business without addresses" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(address = None)))))
        )

        await(service.fetchFirstAddress(testReference)) mustBe Right(None)
      }
      "there are no remaining businesses" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(soleTraderBusinesses.copy(businesses = Seq.empty[SoleTraderBusiness])))
        )

        await(service.fetchFirstAddress(testReference)) mustBe Right(None)
      }
      "there are no sole trader businesses" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(Right(None))

        await(service.fetchFirstAddress(testReference)) mustBe Right(None)
      }
    }
    "return an error" when {
      "there was a problem getting the sole trader businesses" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Left(GetSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.fetchFirstAddress(testReference)) mustBe
          Left(GetSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
      }
    }
  }

  "fetchFirstBusinessName" must {
    "return a business name" when {
      "a business name already exists" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(Right(Some(soleTraderBusinesses)))

        await(service.fetchFirstBusinessName(testReference)) mustBe Right(Some(name))
      }
    }
    "return no business name" when {
      "there are multiple businesses without business names" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(name = None), soleTraderBusinessTwo()))))
        )

        await(service.fetchFirstBusinessName(testReference)) mustBe Right(None)
      }
      "there is a single business without a business name" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(name = None)))))
        )

        await(service.fetchFirstBusinessName(testReference)) mustBe Right(None)
      }
      "there are no remaining businesses" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(soleTraderBusinesses.copy(businesses = Seq.empty[SoleTraderBusiness])))
        )

        await(service.fetchFirstBusinessName(testReference)) mustBe Right(None)
      }
      "there are no sole trader businesses" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(Right(None))

        await(service.fetchFirstBusinessName(testReference)) mustBe Right(None)
      }
    }
    "return an error" when {
      "there was a problem getting the sole trader businesses" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Left(GetSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.fetchFirstBusinessName(testReference)) mustBe
          Left(GetSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
      }
    }
  }

  "fetchAllNameTradeCombos" must {
    "return the trade name combos list" when {
      "a business exists with all information" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(Right(Some(soleTraderBusinesses)))

        await(service.fetchAllNameTradeCombos(testReference)) mustBe Right(Seq(
          (id, Some(name), Some(trade))
        ))
      }
      "a business exists with minimal information" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(soleTraderBusinesses.copy(businesses = Seq(soleTraderBusiness.copy(name = None, trade = None)))))
        )

        await(service.fetchAllNameTradeCombos(testReference)) mustBe Right(Seq(
          (id, None, None)
        ))
      }
      "multiple businesses exist" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(multipleSoleTraderBusinesses(soleTraderBusinessTwo())))
        )

        await(service.fetchAllNameTradeCombos(testReference)) mustBe Right(Seq(
          (id, Some(name), Some(trade)),
          (s"$id-2", None, None)
        ))
      }
      "no businesses are present in the sole trader businesses" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(Some(soleTraderBusinesses.copy(businesses = Seq.empty[SoleTraderBusiness])))
        )

        await(service.fetchAllNameTradeCombos(testReference)) mustBe Right(Seq())
      }
      "no sole trader businesses was found" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Right(None)
        )

        await(service.fetchAllNameTradeCombos(testReference)) mustBe Right(Seq())
      }
    }
    "return an error" when {
      "there was a problem fetching the sole trader businesses" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Left(GetSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.fetchAllNameTradeCombos(testReference)) mustBe
          Left(GetSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
      }
    }
  }

  "fetchStreamlineBusiness" should {
    "return a GetSelfEmploymentsFailure" when {
      "there was a problem retrieving businesses" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(
          Left(GetSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
        )

        await(service.fetchStreamlineBusiness(testReference, id)) mustBe
          Left(GetSelfEmploymentsHttpParser.UnexpectedStatusFailure(INTERNAL_SERVER_ERROR))
      }
    }
    "return a streamline business" when {
      "there are no businesses currently" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(Right(None))

        await(service.fetchStreamlineBusiness(testReference, id)) mustBe
          Right(StreamlineBusiness(None, None, None, None, None, isFirstBusiness = true))
      }
      "the requested business does not exist in the list of existing businesses" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(Right(Some(soleTraderBusinesses)))

        await(service.fetchStreamlineBusiness(testReference, s"$id-2")) mustBe
          Right(StreamlineBusiness(None, None, None, None, Some(accountingMethod), isFirstBusiness = false))
      }
      "the requested business exists in the list and it's the first business" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(Right(Some(soleTraderBusinesses)))

        await(service.fetchStreamlineBusiness(testReference, id)) mustBe
          Right(StreamlineBusiness(
            trade = Some(trade),
            name = Some(name),
            startDate = Some(date),
            startDateBeforeLimit = Some(false),
            accountingMethod = Some(accountingMethod),
            isFirstBusiness = true
          ))
      }
      "the requested business exists in the list and it's not the first business" in new Setup {
        mockGetSubscriptionDetails(testReference, soleTraderBusinessesKey)(Right(Some(multipleSoleTraderBusinesses())))

        await(service.fetchStreamlineBusiness(testReference, s"$id-2")) mustBe
          Right(StreamlineBusiness(
            trade = None,
            name = None,
            startDate = None,
            startDateBeforeLimit = None,
            accountingMethod = Some(accountingMethod),
            isFirstBusiness = false
          ))
      }
    }
  }

}
