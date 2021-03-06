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
package tech.beshu.ror.es.request

import monix.eval.Task
import monix.execution.Scheduler
import org.apache.logging.log4j.scala.Logging
import org.elasticsearch.action._
import org.elasticsearch.action.admin.cluster.repositories.delete.DeleteRepositoryRequest
import org.elasticsearch.action.admin.cluster.repositories.get.GetRepositoriesRequest
import org.elasticsearch.action.admin.cluster.repositories.put.PutRepositoryRequest
import org.elasticsearch.action.admin.cluster.repositories.verify.VerifyRepositoryRequest
import org.elasticsearch.action.admin.cluster.snapshots.create.CreateSnapshotRequest
import org.elasticsearch.action.admin.cluster.snapshots.delete.DeleteSnapshotRequest
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsRequest
import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotRequest
import org.elasticsearch.action.admin.cluster.snapshots.status.SnapshotsStatusRequest
import org.elasticsearch.action.admin.cluster.state.ClusterStateRequest
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest
import org.elasticsearch.action.admin.indices.shards.IndicesShardStoresRequest
import org.elasticsearch.action.admin.indices.stats.IndicesStatsRequest
import org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateRequest
import org.elasticsearch.action.admin.indices.template.get.GetIndexTemplatesRequest
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequest
import org.elasticsearch.action.bulk.{BulkRequest, BulkShardRequest}
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.get.MultiGetRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.{MultiSearchRequest, SearchRequest}
import org.elasticsearch.action.support.ActionFilterChain
import org.elasticsearch.action.termvectors.MultiTermVectorsRequest
import org.elasticsearch.index.reindex.ReindexRequest
import org.elasticsearch.rest.RestChannel
import org.elasticsearch.tasks.{Task => EsTask}
import org.elasticsearch.threadpool.ThreadPool
import tech.beshu.ror.accesscontrol.AccessControlStaticContext
import tech.beshu.ror.boot.Engine
import tech.beshu.ror.es.RorClusterService
import tech.beshu.ror.es.request.AclAwareRequestFilter.EsContext
import tech.beshu.ror.es.request.context.types.{ReindexEsRequestContext, _}
import tech.beshu.ror.es.request.handler.regular.RegularRequestHandler
import tech.beshu.ror.es.request.handler.usermetadata.CurrentUserMetadataRequestHandler
import tech.beshu.ror.es.rradmin.RRAdminRequest

import scala.language.postfixOps
import scala.reflect.ClassTag

class AclAwareRequestFilter(clusterService: RorClusterService,
                            threadPool: ThreadPool)
                           (implicit scheduler: Scheduler)
  extends Logging {

  def handle(engine: Engine,
             esContext: EsContext): Task[Unit] = {
    esContext.actionRequest match {
      case request: RRAdminRequest =>
        val handler = new CurrentUserMetadataRequestHandler(engine, esContext, threadPool)
        handler.handle(new CurrentUserMetadataEsRequestContext(request, esContext, clusterService, threadPool))
      case _ =>
        val regularRequestHandler = new RegularRequestHandler(engine, esContext, threadPool)
        handleEsRestApiRequest(regularRequestHandler, esContext, engine.context)
    }
  }

  private def handleEsRestApiRequest(regularRequestHandler: RegularRequestHandler,
                                     esContext: EsContext,
                                     aclContext: AccessControlStaticContext) = {
    esContext.actionRequest match {
      // snapshots
      case request: GetSnapshotsRequest =>
        regularRequestHandler.handle(new GetSnapshotsEsRequestContext(request, esContext, clusterService, threadPool))
      case request: CreateSnapshotRequest =>
        regularRequestHandler.handle(new CreateSnapshotEsRequestContext(request, esContext, clusterService, threadPool))
      case request: DeleteSnapshotRequest =>
        regularRequestHandler.handle(new DeleteSnapshotEsRequestContext(request, esContext, clusterService, threadPool))
      case request: RestoreSnapshotRequest =>
        regularRequestHandler.handle(new RestoreSnapshotEsRequestContext(request, esContext, clusterService, threadPool))
      case request: SnapshotsStatusRequest =>
        regularRequestHandler.handle(new SnapshotsStatusEsRequestContext(request, esContext, clusterService, threadPool))
      // repositories
      case request: GetRepositoriesRequest =>
        regularRequestHandler.handle(new GetRepositoriesEsRequestContext(request, esContext, clusterService, threadPool))
      case request: PutRepositoryRequest =>
        regularRequestHandler.handle(new CreateRepositoryEsRequestContext(request, esContext, clusterService, threadPool))
      case request: DeleteRepositoryRequest =>
        regularRequestHandler.handle(new DeleteRepositoryEsRequestContext(request, esContext, clusterService, threadPool))
      case request: VerifyRepositoryRequest =>
        regularRequestHandler.handle(new VerifyRepositoryEsRequestContext(request, esContext, clusterService, threadPool))
      // templates
      case request: GetIndexTemplatesRequest =>
        regularRequestHandler.handle(new GetTemplatesEsRequestContext(request, esContext, clusterService, threadPool))
      case request: PutIndexTemplateRequest =>
        regularRequestHandler.handle(new CreateTemplateEsRequestContext(request, esContext, clusterService, threadPool))
      case request: DeleteIndexTemplateRequest =>
        regularRequestHandler.handle(new DeleteTemplateEsRequestContext(request, esContext, clusterService, threadPool))
      // indices
      case request: BulkShardRequest =>
        regularRequestHandler.handle(new BulkShardEsRequestContext(request, esContext, aclContext, clusterService, threadPool))
      case request: IndexRequest =>
        regularRequestHandler.handle(new IndexEsRequestContext(request, esContext, aclContext, clusterService, threadPool))
      case request: MultiGetRequest =>
        regularRequestHandler.handle(new MultiGetEsRequestContext(request, esContext, aclContext, clusterService, threadPool))
      case request: SearchRequest =>
        regularRequestHandler.handle(new SearchEsRequestContext(request, esContext, aclContext, clusterService, threadPool))
      case request: MultiSearchRequest =>
        regularRequestHandler.handle(new MultiSearchEsRequestContext(request, esContext, aclContext, clusterService, threadPool))
      case request: MultiTermVectorsRequest =>
        regularRequestHandler.handle(new MultiTermVectorsEsRequestContext(request, esContext, aclContext, clusterService, threadPool))
      case request: BulkRequest =>
        regularRequestHandler.handle(new BulkEsRequestContext(request, esContext, aclContext, clusterService, threadPool))
      case request: DeleteRequest =>
        regularRequestHandler.handle(new DeleteDocumentEsRequestContext(request, esContext, aclContext, clusterService, threadPool))
      case request: IndicesAliasesRequest =>
        regularRequestHandler.handle(new IndicesAliasesEsRequestContext(request, esContext, aclContext, clusterService, threadPool))
      case request: GetSettingsRequest =>
        regularRequestHandler.handle(new GetSettingsEsRequestContext(request, esContext, aclContext, clusterService, threadPool))
      case request: IndicesStatsRequest =>
        regularRequestHandler.handle(new IndicesStatsEsRequestContext(request, esContext, aclContext, clusterService, threadPool))
      case request: IndicesShardStoresRequest =>
        regularRequestHandler.handle(new IndicesShardStoresEsRequestContext(request, esContext, aclContext, clusterService, threadPool))
      case request: ClusterStateRequest =>
        TemplateClusterStateEsRequestContext.from(request, esContext, clusterService, threadPool) match {
          case Some(requestContext) =>
            regularRequestHandler.handle(requestContext)
          case None =>
            regularRequestHandler.handle(new ClusterStateEsRequestContext(request, esContext, aclContext, clusterService, threadPool))
        }
      case request: IndicesRequest.Replaceable =>
        regularRequestHandler.handle(new IndicesReplaceableEsRequestContext(request, esContext, aclContext, clusterService, threadPool))
      case request: ReindexRequest =>
        regularRequestHandler.handle(new ReindexEsRequestContext(request, esContext, aclContext, clusterService, threadPool))
      // rest
      case _ =>
        handleSearchTemplateRequest(regularRequestHandler, esContext, aclContext) orElse
          handleReflectionBasedIndicesRequest(regularRequestHandler, esContext, aclContext) getOrElse
          handleGeneralNonIndexOperation(regularRequestHandler, esContext)
    }
  }

  private def handleSearchTemplateRequest(regularRequestHandler: RegularRequestHandler,
                                          esContext: EsContext,
                                          aclContext: AccessControlStaticContext) = {
    SearchTemplateEsRequestContext
      .from(esContext.actionRequest, esContext, aclContext, clusterService, threadPool)
      .map(regularRequestHandler.handle(_))
  }

  private def handleReflectionBasedIndicesRequest(regularRequestHandler: RegularRequestHandler,
                                                  esContext: EsContext,
                                                  aclContext: AccessControlStaticContext) = {
    ReflectionBasedIndicesEsRequestContext
      .from(esContext.actionRequest, esContext, aclContext, clusterService, threadPool)
      .map(regularRequestHandler.handle(_))
  }

  private def handleGeneralNonIndexOperation(regularRequestHandler: RegularRequestHandler, esContext: EsContext) = {
    regularRequestHandler.handle {
      new GeneralNonIndexEsRequestContext(esContext.actionRequest, esContext, clusterService, threadPool)
    }
  }
}

object AclAwareRequestFilter {
  final case class EsContext(channel: RestChannel,
                             task: EsTask,
                             actionType: String,
                             actionRequest: ActionRequest,
                             listener: ActionListener[ActionResponse],
                             chain: ActionFilterChain[ActionRequest, ActionResponse],
                             crossClusterSearchEnabled: Boolean,
                             involveFilters: Boolean)
}

final case class RequestSeemsToBeInvalid[T: ClassTag](message: String)
  extends IllegalStateException(s"Request '${implicitly[ClassTag[T]].runtimeClass.getSimpleName}' cannot be handled; [msg: $message]")