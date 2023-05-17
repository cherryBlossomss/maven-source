/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.internal.aether;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.Objects;

import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectStepData;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;

import static java.util.Objects.requireNonNull;

/**
 * A class building reverse tree using {@link CollectStepData} trace data provided in {@link RepositoryEvent}
 * events fired during collection.
 *
 * @since 3.9.0
 */
class ReverseTreeRepositoryListener extends AbstractRepositoryListener {
    @Override
    public void artifactResolved(RepositoryEvent event) {
        requireNonNull(event, "event cannot be null");

        if (!isLocalRepositoryArtifact(event.getSession(), event.getArtifact())) {
            return;
        }

        CollectStepData collectStepTrace = lookupCollectStepData(event.getTrace());
        if (collectStepTrace == null) {
            return;
        }

        Artifact resolvedArtifact = event.getArtifact();
        Artifact nodeArtifact = collectStepTrace.getNode().getArtifact();

        if (isInScope(resolvedArtifact, nodeArtifact)) {
            Dependency node = collectStepTrace.getNode();
            ArrayList<String> trackingData = new ArrayList<>();
            trackingData.add(node + " (" + collectStepTrace.getContext() + ")");
            String indent = "";
            ListIterator<DependencyNode> iter = collectStepTrace
                    .getPath()
                    .listIterator(collectStepTrace.getPath().size());
            while (iter.hasPrevious()) {
                DependencyNode curr = iter.previous();
                indent += "  ";
                trackingData.add(indent + curr + " (" + collectStepTrace.getContext() + ")");
            }
            try {
                Path trackingDir =
                        resolvedArtifact.getFile().getParentFile().toPath().resolve(".tracking");
                Files.createDirectories(trackingDir);
                Path trackingFile = trackingDir.resolve(collectStepTrace
                        .getPath()
                        .get(0)
                        .getArtifact()
                        .toString()
                        .replace(":", "_"));
                Files.write(trackingFile, trackingData, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * Returns {@code true} if passed in artifact is originating from local repository. In other words, we want
     * to process and store tracking information ONLY into local repository, not to any other place. This method
     * filters out currently built artifacts, as events are fired for them as well, but their resolved artifact
     * file would point to checked out source-tree, not the local repository.
     * <p>
     * Visible for testing.
     */
    static boolean isLocalRepositoryArtifact(RepositorySystemSession session, Artifact artifact) {
        return artifact.getFile()
                .getPath()
                .startsWith(session.getLocalRepository().getBasedir().getPath());
    }

    /**
     * Unravels trace tree (going upwards from current node), looking for {@link CollectStepData} trace data.
     * This method may return {@code null} if no collect step data found in passed trace data or it's parents.
     * <p>
     * Visible for testing.
     */
    static CollectStepData lookupCollectStepData(RequestTrace trace) {
        CollectStepData collectStepTrace = null;
        while (trace != null) {
            if (trace.getData() instanceof CollectStepData) {
                collectStepTrace = (CollectStepData) trace.getData();
                break;
            }
            trace = trace.getParent();
        }
        return collectStepTrace;
    }

    /**
     * The event "artifact resolved" if fired WHENEVER an artifact is resolved, BUT it happens also when an artifact
     * descriptor (model, the POM) is being built, and parent (and parent of parent...) is being asked for. Hence, this
     * method "filters" out in WHICH artifact are we interested in, but it intentionally neglects extension as
     * ArtifactDescriptorReader modifies extension to "pom" during collect. So all we have to rely on is GAV only.
     */
    static boolean isInScope(Artifact artifact, Artifact nodeArtifact) {
        return Objects.equals(artifact.getGroupId(), nodeArtifact.getGroupId())
                && Objects.equals(artifact.getArtifactId(), nodeArtifact.getArtifactId())
                && Objects.equals(artifact.getVersion(), nodeArtifact.getVersion());
    }
}
