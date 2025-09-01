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

package uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.utils

import org.mockito.Mockito.when
import org.scalatestplus.play.PlaySpec
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, SEE_OTHER}
import play.api.mvc.Results._
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout, redirectLocation, status}
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.AppConfig
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.mocks.MockAppConfig
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.services.SessionDataService
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.services.mocks.MockSessionDataService

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.{ExecutionContext, Future}

class ReferenceRetrievalSpec extends PlaySpec with MockSessionDataService with MockAppConfig {

  object TestReferenceRetrieval extends ReferenceRetrieval {
    override val sessionDataService: SessionDataService = mockSessionDataService
    override val appConfig: AppConfig = mockAppConfig
    override implicit val ec: ExecutionContext = Implicits.global
  }

  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  val reference: String = "test-reference"
  val testTaskListUrl: String = "/test-task-list-url"
  val testAgentTaskListUrl: String = "/test-agent-task-list-url"

  "withIndividualReference" should {
    "return the reference requested" when {
      "the reference was successfully returned from the session store" in {
        mockFetchReferenceSuccess(Some(reference))
        when(mockAppConfig.yourIncomeSourcesUrl) thenReturn testTaskListUrl

        val result = TestReferenceRetrieval.withIndividualReference { reference =>
          Future.successful(Ok(reference))
        }

        status(result) mustBe OK
        contentAsString(result) mustBe reference
      }
    }
    "redirect to the individual task list page" when {
      "no reference was found in the session store" in {
        mockFetchReferenceSuccess(None)
        when(mockAppConfig.yourIncomeSourcesUrl) thenReturn testTaskListUrl

        val result = TestReferenceRetrieval.withIndividualReference { _ =>
          Future.successful(Redirect(testTaskListUrl))
        }

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some(testTaskListUrl)
      }
    }
    "throw an InternalServerException" when {
      "an error was returned from the session store" in {
        mockFetchReferenceFailure(INTERNAL_SERVER_ERROR)
        when(mockAppConfig.yourIncomeSourcesUrl) thenReturn testTaskListUrl

        intercept[InternalServerException](await(TestReferenceRetrieval.withIndividualReference { _ =>
          Future.successful(Ok("test-failure"))
        })).message mustBe s"[ReferenceRetrieval][withReference] - Error occurred when fetching reference from session"
      }
    }
  }

  "withAgentReference" should {
    "return the reference requested" when {
      "the reference was successfully returned from the session store" in {
        mockFetchReferenceSuccess(Some(reference))
        when(mockAppConfig.clientYourIncomeSourcesUrl) thenReturn testAgentTaskListUrl

        val result = TestReferenceRetrieval.withAgentReference { reference =>
          Future.successful(Ok(reference))
        }

        status(result) mustBe OK
        contentAsString(result) mustBe reference
      }
    }
    "redirect to the individual task list page" when {
      "no reference was found in the session store" in {
        mockFetchReferenceSuccess(None)
        when(mockAppConfig.clientYourIncomeSourcesUrl) thenReturn testAgentTaskListUrl

        val result = TestReferenceRetrieval.withAgentReference { _ =>
          Future.successful(Ok("test-redirect"))
        }

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some(testAgentTaskListUrl)
      }
    }
    "throw an InternalServerException" when {
      "an error was returned from the session store" in {
        mockFetchReferenceFailure(INTERNAL_SERVER_ERROR)
        when(mockAppConfig.clientYourIncomeSourcesUrl) thenReturn testAgentTaskListUrl

        intercept[InternalServerException](await(TestReferenceRetrieval.withAgentReference { _ =>
          Future.successful(Ok("test-failure"))
        })).message mustBe s"[ReferenceRetrieval][withReference] - Error occurred when fetching reference from session"
      }
    }
  }

}
