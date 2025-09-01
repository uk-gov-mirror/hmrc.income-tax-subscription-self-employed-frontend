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

import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.ClientDetails

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClientDetailsRetrieval @Inject()(sessionDataService: SessionDataService)(implicit ec: ExecutionContext) {


  def getClientDetails(implicit request: Request[_], hc: HeaderCarrier): Future[ClientDetails] = {
    sessionDataService.fetchNino map {
      case Right(Some(nino)) =>
        ClientDetails(
          name = Seq(request.session.get("FirstName"), request.session.get("LastName")).flatten.mkString(" "),
          nino = nino
        )
      case Right(None) => throw new NoNinoInSessionException
      case Left(error) => throw new FetchFromSessionException(error.toString)
    }
  }

  private class NoNinoInSessionException extends InternalServerException(
    s"[ClientDetailsRetrieval][getClientDetails] - no nino value present in session"
  )

  private class FetchFromSessionException(error: String) extends InternalServerException(
    s"[ClientDetailsRetrieval][getClientDetails] - failure when fetching nino from session: $error"
  )
  
}
