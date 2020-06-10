/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.models.sessions.infinispan.initializer;

import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.KeycloakSessionTask;
import org.keycloak.models.utils.KeycloakModelUtils;

import java.io.Serializable;
import java.util.function.Function;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public class SessionInitializerWorker implements Function<EmbeddedCacheManager, SessionLoader.WorkerResult>, Serializable {

    private static final Logger log = Logger.getLogger(SessionInitializerWorker.class);

    private SessionLoader.LoaderContext loaderCtx;
    private SessionLoader.WorkerContext workerCtx;
    private SessionLoader sessionLoader;

    private String cacheName;


    public void setWorkerEnvironment(SessionLoader.LoaderContext loaderCtx, SessionLoader.WorkerContext workerCtx, SessionLoader sessionLoader, String cacheName) {
        this.loaderCtx = loaderCtx;
        this.workerCtx = workerCtx;
        this.sessionLoader = sessionLoader;
        this.cacheName = cacheName;
    }


    @Override
    public SessionLoader.WorkerResult apply(EmbeddedCacheManager embeddedCacheManager) {
        Cache<Object, Object> workCache = embeddedCacheManager.getCache(cacheName);
        if (log.isTraceEnabled()) {
            log.tracef("Running computation for segment %s with worker %s", workerCtx.getSegment(), workerCtx.getWorkerId());
        }

        KeycloakSessionFactory sessionFactory = workCache.getAdvancedCache().getComponentRegistry().getComponent(KeycloakSessionFactory.class);
        if (sessionFactory == null) {
            log.debugf("KeycloakSessionFactory not yet set in cache. Worker skipped");
            return sessionLoader.createFailedWorkerResult(loaderCtx, workerCtx);
        }

        SessionLoader.WorkerResult[] ref = new SessionLoader.WorkerResult[1];
        KeycloakModelUtils.runJobInTransaction(sessionFactory, new KeycloakSessionTask() {

            @Override
            public void run(KeycloakSession session) {
                ref[0] = sessionLoader.loadSessions(session, loaderCtx, workerCtx);
            }

        });

        return ref[0];
    }
}
