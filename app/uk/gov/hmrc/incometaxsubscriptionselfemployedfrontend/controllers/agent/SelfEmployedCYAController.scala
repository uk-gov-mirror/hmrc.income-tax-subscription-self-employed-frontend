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

package uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.agent

import play.api.mvc._
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.AppConfig
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitch.RemoveAccountingMethod
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.config.featureswitch.FeatureSwitching
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.controllers.utils.ReferenceRetrieval
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.models.{SelfEmploymentsCYAModel, SoleTraderBusiness}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.services.{AuthService, ClientDetailsRetrieval, MultipleSelfEmploymentsService, SessionDataService}
import uk.gov.hmrc.incometaxsubscriptionselfemployedfrontend.views.html.agent.SelfEmployedCYA
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SelfEmployedCYAController @Inject()(checkYourAnswersView: SelfEmployedCYA,
                                          clientDetailsRetrieval: ClientDetailsRetrieval,
                                          authService: AuthService,
                                          multipleSelfEmploymentsService: MultipleSelfEmploymentsService,
                                          mcc: MessagesControllerComponents)
                                         (val sessionDataService: SessionDataService,
                                          val appConfig: AppConfig)
                                         (implicit val ec: ExecutionContext)
  extends FrontendController(mcc) with ReferenceRetrieval with FeatureSwitching {

  def show(id: String, isEditMode: Boolean, isGlobalEdit: Boolean): Action[AnyContent] = Action.async { implicit request =>
    authService.authorised() {
      withAgentReference { reference =>
        withSelfEmploymentCYAModel(reference, id) { selfEmploymentCYAModel =>
          clientDetailsRetrieval.getClientDetails map { clientDetails =>
            Ok(checkYourAnswersView(
              answers = selfEmploymentCYAModel,
              postAction = routes.SelfEmployedCYAController.submit(id, isGlobalEdit),
              backUrl = backUrl(isEditMode, isGlobalEdit, selfEmploymentCYAModel.confirmed),
              clientDetails = clientDetails,
              isGlobalEdit = isGlobalEdit
            ))
          }
        }
      }
    }
  }

  def submit(id: String, isGlobalEdit: Boolean): Action[AnyContent] = Action.async { implicit request =>
    withAgentReference { reference =>
      withSelfEmploymentCYAModel(reference, id) { selfEmploymentCYAModel =>
        if (selfEmploymentCYAModel.isComplete(isEnabled(RemoveAccountingMethod))) {
          multipleSelfEmploymentsService.confirmBusiness(reference, id) map {
            case Right(_) =>
              if (isGlobalEdit) Redirect(appConfig.globalCYAUrl)
              else Redirect(appConfig.clientYourIncomeSourcesUrl)
            case Left(_) =>
              throw new InternalServerException("[SelfEmployedCYAController][submit] - Could not confirm self employment business")
          }
        } else {
          Future.successful(Redirect(appConfig.clientYourIncomeSourcesUrl))
        }
      }
    }
  }

  private def withSelfEmploymentCYAModel(reference: String, id: String)(f: SelfEmploymentsCYAModel => Future[Result])
                                        (implicit hc: HeaderCarrier): Future[Result] =
    for {
      (businesses, accountingMethod) <- fetchBusinessListAndAccountingMethod(reference)
      business = businesses.find(_.id == id)
      isFirstBusiness = businesses.headOption.exists(_.id == id)
      result <- f(SelfEmploymentsCYAModel(id, business, accountingMethod, businesses.length, isFirstBusiness))
    } yield result

  private def fetchBusinessListAndAccountingMethod(reference: String)(implicit hc: HeaderCarrier) = {
    multipleSelfEmploymentsService.fetchSoleTraderBusinesses(reference)
      .map(_.getOrElse(throw new FetchSoleTraderBusinessesException))
      .map {
        case Some(soleTraderBusinesses) => (soleTraderBusinesses.businesses, soleTraderBusinesses.accountingMethod)
        case None => (Seq.empty[SoleTraderBusiness], None)
      }
  }

  private class FetchSoleTraderBusinessesException extends InternalServerException(
    "[SelfEmployedCYAController][fetchBusinessListAndAccountingMethod] - Failed to retrieve sole trader businesses"
  )

  def backUrl(isEditMode: Boolean, isGlobalEdit: Boolean, isConfirmed: Boolean): Option[String] = {
    if (isGlobalEdit && isConfirmed) {
      Some(appConfig.globalCYAUrl)
    } else if (isEditMode || isGlobalEdit) {
      Some(appConfig.clientYourIncomeSourcesUrl)
    } else
      None
  }

}
