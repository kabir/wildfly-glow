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
package org.wildfly.glow.cli.commands;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.wildfly.glow.Arguments;
import static org.wildfly.glow.Arguments.CLOUD_EXECUTION_CONTEXT;
import static org.wildfly.glow.Arguments.COMPACT_PROPERTY;
import org.wildfly.glow.DockerSupport;
import org.wildfly.glow.FeaturePacks;
import org.wildfly.glow.GlowMessageWriter;
import org.wildfly.glow.GlowSession;
import org.wildfly.glow.HiddenPropertiesAccessor;
import org.wildfly.glow.OutputFormat;
import static org.wildfly.glow.OutputFormat.BOOTABLE_JAR;
import static org.wildfly.glow.OutputFormat.DOCKER_IMAGE;
import static org.wildfly.glow.OutputFormat.PROVISIONING_XML;
import static org.wildfly.glow.OutputFormat.SERVER;
import org.wildfly.glow.ScanArguments.Builder;
import org.wildfly.glow.ScanResults;
import org.wildfly.glow.maven.MavenResolver;

import picocli.CommandLine;
import picocli.CommandLine.Parameters;

@CommandLine.Command(
        name = Constants.SCAN_COMMAND,
        sortOptions = true
)
public class ScanCommand extends AbstractCommand {

    @CommandLine.Option(names = {Constants.CLOUD_OPTION_SHORT, Constants.CLOUD_OPTION})
    Optional<Boolean> cloud;

    @CommandLine.Option(names = {Constants.WILDFLY_PREVIEW_OPTION_SHORT, Constants.WILDFLY_PREVIEW_OPTION})
    Optional<Boolean> wildflyPreview;

    @CommandLine.Option(names = {Constants.SUGGEST_OPTION_SHORT, Constants.SUGGEST_OPTION})
    Optional<Boolean> suggest;

    @CommandLine.Option(names = Constants.HA_OPTION)
    Optional<Boolean> haProfile;

    @CommandLine.Option(names = {Constants.SERVER_VERSION_OPTION_SHORT, Constants.SERVER_VERSION_OPTION}, paramLabel = Constants.SERVER_VERSION_OPTION_LABEL)
    Optional<String> wildflyServerVersion;

    @CommandLine.Option(names = {Constants.DOCKER_IMAGE_NAME_OPTION_SHORT, Constants.DOCKER_IMAGE_NAME_OPTION}, paramLabel = Constants.DOCKER_IMAGE_NAME_OPTION_LABEL)
    Optional<String> dockerImageName;

    @CommandLine.Option(names = Constants.ADD_LAYERS_FOR_JNDI_OPTION, split = ",", paramLabel = Constants.ADD_LAYERS_FOR_JNDI_OPTION_LABEL)
    Set<String> layersForJndi = new LinkedHashSet<>();

    @CommandLine.Option(names = {Constants.ADD_ONS_OPTION_SHORT, Constants.ADD_ONS_OPTION}, split = ",", paramLabel = Constants.ADD_ONS_OPTION_LABEL)
    Set<String> addOns = new LinkedHashSet<>();

    @Parameters(descriptionKey = "deployments")
    List<Path> deployments;

    @CommandLine.Option(names = Constants.INPUT_FEATURE_PACKS_FILE_OPTION, paramLabel = Constants.INPUT_FEATURE_PACKS_FILE_OPTION_LABEL)
    Optional<Path> provisioningXml;

    @CommandLine.Option(names = {Constants.PROVISION_OPTION_SHORT, Constants.PROVISION_OPTION}, paramLabel = Constants.PROVISION_OPTION_LABEL)
    Optional<OutputFormat> provision;

    @Override
    public Integer call() throws Exception {
        HiddenPropertiesAccessor hiddenPropertiesAccessor = new HiddenPropertiesAccessor();
        boolean compact = Boolean.parseBoolean(hiddenPropertiesAccessor.getProperty(COMPACT_PROPERTY));
        if (!compact) {
            print("Wildfly Glow is scanning...");
        }
        Builder builder = Arguments.scanBuilder();
        if (cloud.orElse(false)) {
            builder.setExecutionContext(CLOUD_EXECUTION_CONTEXT);
        }
        if (haProfile.orElse(false)) {
            Set<String> profiles = new HashSet<>();
            profiles.add(Constants.HA);
            builder.setExecutionProfiles(profiles);
        }
        if (!layersForJndi.isEmpty()) {
            builder.setJndiLayers(layersForJndi);
        }
        if (suggest.orElse(false)) {
            builder.setSuggest(true);
        }
        if (wildflyPreview.orElse(false)) {
            builder.setTechPreview(true);
        }
        if (wildflyServerVersion.isPresent()) {
            builder.setVersion(wildflyServerVersion.get());
        }
        builder.setVerbose(verbose);
        if (!addOns.isEmpty()) {
            builder.setUserEnabledAddOns(addOns);
        }
        if (deployments != null && !deployments.isEmpty()) {
            builder.setBinaries(deployments);
        }
        if (provisioningXml.isPresent()) {
            builder.setProvisoningXML(provisioningXml.get());
        }
        if (provision.isPresent()) {
            if (BOOTABLE_JAR.equals(provision.get()) && cloud.orElse(false)) {
                throw new Exception("Can't produce a Bootable JAR for cloud. Use the " + Constants.PROVISION_OPTION + "=SERVER option for cloud.");
            }
            if (DOCKER_IMAGE.equals(provision.get()) && !cloud.orElse(false)) {
                throw new Exception("Can't produce a Docker image if cloud is not enabled. Use the " + Constants.CLOUD_OPTION + " option.");
            }
            builder.setOutput(provision.get());
        }
        if (dockerImageName.isPresent()) {
            if (provision.isPresent() && !DOCKER_IMAGE.equals(provision.get())) {
                throw new Exception("Can only set a docker image name when provisioning a docker image. Remove the " + Constants.DOCKER_IMAGE_NAME_OPTION + " option");
            }
        }
        ScanResults scanResults = GlowSession.scan(MavenResolver.newMavenResolver(), builder.build(), GlowMessageWriter.DEFAULT);
        scanResults.outputInformation();
        if (provision.isEmpty()) {
            if (!compact) {
                if (suggest.orElse(false)) {
                    if (!scanResults.getSuggestions().getPossibleAddOns().isEmpty() && addOns.isEmpty()) {
                        print("@|bold To enable add-ons add the|@ @|fg(yellow) %s=<list of add-ons>|@ @|bold option to the|@ @|fg(yellow) %s|@ @|bold command|@", Constants.ADD_ONS_OPTION, Constants.SCAN_COMMAND);
                    }
                    if (!scanResults.getSuggestions().getPossibleProfiles().isEmpty()) {
                        print("@|bold To enable the HA profile add the|@ @|fg(yellow) %s|@ @|bold option to the|@ @|fg(yellow) %s|@ @|bold command|@", Constants.HA_OPTION, Constants.SCAN_COMMAND);
                    }
                }
                print("@|bold To provision the WildFly server for your deployment add the|@ @|fg(yellow) %s=SERVER|@ @|bold option to the|@ @|fg(yellow) %s|@ @|bold command|@", Constants.PROVISION_OPTION, Constants.SCAN_COMMAND);
            }
        } else {
            print();
            Path target = null;
            String vers = wildflyServerVersion.orElse(null) == null ? FeaturePacks.getLatestVersion() : wildflyServerVersion.get();
            String doneMessage = null;
            switch (provision.get()) {
                case BOOTABLE_JAR: {
                    target = Paths.get("");
                    print("@|bold Building WildFly Bootable JAR file|@");
                    break;
                }
                case PROVISIONING_XML: {
                    target = Paths.get("server-" + vers);
                    print("@|bold Generating provisioning configuration in %s/provisioning.xml file|@", target);
                    doneMessage = "@|bold Generation DONE. Provisioning configuration is located in " + target + "/provisioning.xml file|@";
                    break;
                }
                case SERVER: {
                    target = Paths.get("server-" + vers);
                    print("@|bold Provisioning server in %s directory|@", target);
                    if (cloud.orElse(false)) {
                        doneMessage = "@|bold Provisioning DONE. To run the server: 'JBOSS_HOME=" + target + " sh " + target + "/bin/openshift-launch.sh'|@";
                    } else {
                        doneMessage = "@|bold Provisioning DONE. To run the server: 'sh " + target + "/bin/standalone.sh'|@";
                    }
                    break;
                }
                case DOCKER_IMAGE: {
                    target = Paths.get("server-" + vers);
                    String imageName = dockerImageName.isPresent()? dockerImageName.get() : DockerSupport.getImageName(target.toString());
                    print("@|bold Generating docker image '%s'|@", imageName);
                    doneMessage = "@|bold To run the image call: 'docker run " + imageName + "'|@";
                    break;
                }
            }
            Path actualTarget = scanResults.outputConfig(target, dockerImageName.orElse(null));
            if (BOOTABLE_JAR.equals(provision.get())) {
                doneMessage = "@|bold Bootable JAR build DONE. To run the jar: 'java -jar " + actualTarget + "'|@";
            } else {
                if (DOCKER_IMAGE.equals(provision.get())) {
                    print("@|bold Image generation DONE. Docker file generated in %s|@.", actualTarget.toAbsolutePath());
                }
            }
            print(doneMessage);
        }
        return 0;
    }
}
