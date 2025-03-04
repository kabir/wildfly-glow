/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.glow;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface ScanArguments {

    /**
     * @return the executionProfiles
     */
    Set<String> getExecutionProfiles();

    /**
     * @return the userEnabledAddOns
     */
    Set<String> getUserEnabledAddOns();

    /**
     * @return the binaries
     */
    List<Path> getBinaries();

    /**
     * @return the provisioningXML
     */
    Path getProvisioningXML();

    /**
     * @return the output
     */
    OutputFormat getOutput();

    /**
     * @return the executionContext
     */
    String getExecutionContext();

    /**
     * @return the suggest
     */
    boolean isSuggest();

    boolean isCloud();

    String getVersion();

    Boolean isCompact();

    boolean isTechPreview();

    default Builder createScanArgumentsBuilder() {
        return new Builder();
    }

    class Builder extends GoOfflineArguments.Builder {

        Builder() {
        }

        public Builder setExecutionProfiles(Set<String> executionProfiles) {
            this.executionProfiles = executionProfiles;
            return this;
        }

        public Builder setUserEnabledAddOns(Set<String> userEnabledAddOns) {
            this.userEnabledAddOns = userEnabledAddOns;
            return this;
        }

        public Builder setBinaries(List<Path> binaries) {
            this.binaries = binaries;
            return this;
        }

        public Builder setProvisoningXML(Path provisoningXML) {
            this.provisioningXML = provisoningXML;
            return this;
        }

        public Builder setOutput(OutputFormat output) {
            this.output = output;
            return this;
        }

        public Builder setSuggest(boolean suggest) {
            this.suggest = suggest;
            return this;
        }

        public Builder setVersion(String version) {
            this.version = version;
            return this;
        }

        public Builder setExecutionContext(String executionContext) {
            this.executionContext = executionContext;
            return this;
        }

        public Builder setConfigName(String configName) {
            this.configName = configName;
            return this;
        }

        public Builder setJndiLayers(Set<String> layersForJndi) {
            this.layersForJndi = layersForJndi;
            return this;
        }

        public Builder setVerbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public Builder setTechPreview(boolean techPreview) {
            this.techPreview = techPreview;
            return this;
        }
    }
}
