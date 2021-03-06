/*
 *    This file is part of ReadonlyREST.
 *
 *    ReadonlyREST is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    ReadonlyREST is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with ReadonlyREST.  If not, see http://www.gnu.org/licenses/
 */
package tech.beshu.ror.integration.suites

import org.scalatest.Matchers._
import org.scalatest.WordSpec
import tech.beshu.ror.integration.suites.base.support.BasicSingleNodeEsClusterSupport
import tech.beshu.ror.utils.containers.EsContainerCreator
import tech.beshu.ror.utils.elasticsearch.ClusterManager

trait ClusterStateSuite
  extends WordSpec
    with BasicSingleNodeEsClusterSupport {
  this: EsContainerCreator =>

  override implicit val rorConfigFileName = "/cluster_state/readonlyrest.yml"

  private lazy val adminClusterStateManager = new ClusterManager(adminClient, esVersion = targetEs.esVersion)

  "/_cat/state should work as expected" in {
    val response = adminClusterStateManager.healthCheck()

    response.responseCode should be(200)
  }
}