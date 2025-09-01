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

package uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.individual

import _root_.uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.utilities.UUIDGenerator
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.http.InternalServerException
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.AppConfig
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitch.RemoveAccountingMethod
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitching
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.utils.ReferenceRetrieval
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.SoleTraderBusinesses
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.services.{AuthService, MultipleSelfEmploymentsService, SessionDataService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class InitialiseController @Inject()(mcc: MessagesControllerComponents,
                                     authService: AuthService,
                                     multipleSelfEmploymentsService: MultipleSelfEmploymentsService,
                                     uuidGen: UUIDGenerator)
                                    (val appConfig: AppConfig,
                                     val sessionDataService: SessionDataService)
                                    (implicit val ec: ExecutionContext) extends FrontendController(mcc) with FeatureSwitching with ReferenceRetrieval {

  def initialise: Action[AnyContent] = Action.async { implicit request =>
    val id = uuidGen.generateId

    authService.authorised() {
      withIndividualReference { reference =>
        multipleSelfEmploymentsService.fetchSoleTraderBusinesses(reference) map {
          case Right(Some(SoleTraderBusinesses(_, Some(_)))) =>
            Redirect(routes.FullIncomeSourceController.show(id))
          case Right(_) =>
            if (isEnabled(RemoveAccountingMethod)) {
              Redirect(routes.FullIncomeSourceController.show(id))
            } else {
              Redirect(routes.BusinessAccountingMethodController.show(id))
            }
          case Left(_) =>
            throw new InternalServerException("[InitialiseController][initialise] - Failure fetching sole trader businesses")
        }
      }
    }
  }
}
