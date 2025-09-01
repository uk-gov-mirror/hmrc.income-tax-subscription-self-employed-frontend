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

package uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.utils

import com.google.inject.Inject
import play.api.Logging
import play.api.mvc.Result
import play.api.mvc.Results.Redirect
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.AppConfig
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.services.SessionDataService

import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

trait ReferenceRetrieval extends Logging {

  val sessionDataService: SessionDataService
  val appConfig: AppConfig

  implicit val ec: ExecutionContext

  def withIndividualReference(f: String => Future[Result])
                             (implicit hc: HeaderCarrier): Future[Result] = {
    withReference(f, Redirect(appConfig.yourIncomeSourcesUrl))
  }

  def withAgentReference(f: String => Future[Result])
                        (implicit hc: HeaderCarrier): Future[Result] = {
    withReference(f, Redirect(appConfig.clientYourIncomeSourcesUrl))
  }

  private def withReference(f: String => Future[Result], redirectIfNotPresent: Result)
                           (implicit hc: HeaderCarrier): Future[Result] = {

    sessionDataService.fetchReference flatMap {
      case Right(Some(value)) => f(value)
      case Right(None) => Future.successful(redirectIfNotPresent)
      case Left(_) =>
        throw new InternalServerException(s"[ReferenceRetrieval][withReference] - Error occurred when fetching reference from session")
    }
  }

}

@Singleton
class ReferenceRetrievalInj @Inject()(val sessionDataService: SessionDataService,
                                      val appConfig: AppConfig)
                                     (implicit val ec: ExecutionContext) {

  def getReference(implicit hc: HeaderCarrier): Future[Option[String]] = {
    sessionDataService.fetchReference map {
      case Right(maybeReference) =>
        maybeReference
      case Left(_) =>
        throw new InternalServerException(s"[ReferenceRetrievalInj][getReference] - Error occurred when fetching reference from session")
    }
  }

}