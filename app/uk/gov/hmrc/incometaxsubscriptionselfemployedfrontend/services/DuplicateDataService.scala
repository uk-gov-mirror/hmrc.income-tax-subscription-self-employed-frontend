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

package uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.services

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.connectors.httpparser.GetSelfEmploymentsHttpParser.GetSelfEmploymentsFailure
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.DuplicateDataModel

import scala.concurrent.Future

class DuplicateDataService {
  def saveDuplicateData(
    model: DuplicateDataModel
  )(implicit hc: HeaderCarrier): Future[Either[GetSelfEmploymentsFailure, Boolean]] = {
    Future.successful(Right(true))
  }

  def getDuplicateData(
    reference: String,
    id: String
  )(implicit hc: HeaderCarrier): Future[Either[GetSelfEmploymentsFailure, Option[DuplicateDataModel]]] = {
    Future.successful(Right(None))
  }
}
