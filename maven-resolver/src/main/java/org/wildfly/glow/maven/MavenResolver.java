/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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
package org.wildfly.glow.maven;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.jboss.galleon.maven.plugin.util.MavenArtifactRepositoryManager;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author jdenise
 */
public final class MavenResolver {

    public static final String JBOSS_REPO_URL = "https://repository.jboss.org/nexus/content/groups/public/";
    public static final String CENTRAL_REPO_URL = "https://repo1.maven.org/maven2/";
    public static final String GA_REPO_URL = "https://maven.repository.redhat.com/ga/";

    public static MavenRepoManager newMavenResolver() {
        RepositorySystem repoSystem = MavenResolver.newRepositorySystem();
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository(Paths.get(System.getProperty("user.home"), ".m2", "repository")
                .toFile());
        session.setLocalRepositoryManager(repoSystem.newLocalRepositoryManager(session, localRepo));
        List<RemoteRepository> repos = new ArrayList<>();
        RemoteRepository.Builder central = new RemoteRepository.Builder("central", "default", CENTRAL_REPO_URL);
        repos.add(central.build());
        RemoteRepository.Builder ga = new RemoteRepository.Builder("redhat-ga", "default", GA_REPO_URL);
        repos.add(ga.build());
        RemoteRepository.Builder nexus = new RemoteRepository.Builder("jboss-nexus", "default", JBOSS_REPO_URL);
        repos.add(nexus.build());
        MavenArtifactRepositoryManager resolver
                = new MavenArtifactRepositoryManager(repoSystem, session, repos);
        return resolver;
    }

    static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        return locator.getService(RepositorySystem.class);
    }
}
