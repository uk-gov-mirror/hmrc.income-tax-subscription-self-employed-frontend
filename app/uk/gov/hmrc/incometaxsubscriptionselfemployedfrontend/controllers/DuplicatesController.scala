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

package uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers

import play.api.i18n.I18nSupport
import play.api.mvc.Results.Redirect
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.DuplicateDataModel
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.services.{DuplicateDataService, MultipleSelfEmploymentsService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DuplicatesController @Inject()(
  mcc: MessagesControllerComponents,
  duplicateDataService: DuplicateDataService,
)(implicit val ec: ExecutionContext)
extends FrontendController(mcc) with I18nSupport {
  def show(): Action[AnyContent] = Action.async { implicit request =>
    val reference = request.session.get("reference")
    val id = request.session.get("id")
    (reference, id) match {
      case (Some(reference), Some(id)) =>
        duplicateDataService.getDuplicateData(reference, id).map {
          case Right(optModel) =>
            val model: DuplicateDataModel = optModel.getOrElse(throw new InternalServerException("[DuplicatesController][show] - No data for trade/name."))
            ???
          case _ =>
            throw new InternalServerException("[DuplicatesController][show] - No data for trade/name.")
        }
      case _ =>
        throw new InternalServerException("[DuplicatesController][show] - Could not read trade/name.")
    }
  }
}

object DuplicatesController {
  def duplicatesFound(
    duplicateDataService: DuplicateDataService,
    reference: String,
    id: String,
    trade: String,
    name: String,
    isAgent: Boolean
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] = {
    duplicateDataService.saveDuplicateData(DuplicateDataModel(
      reference = reference,
      id = id,
      name = name,
      trade = trade,
      isAgent = isAgent
    )).map {
      case Right(_) =>
        Redirect(routes.DuplicatesController.show.url)
          .withSession("reference" -> reference)
          .withSession("id" -> id)
      case Left(_) =>
        throw new InternalServerException("[DuplicatesController][duplicatesFound] - Could not save write trade/name.")
    }
  }
}
