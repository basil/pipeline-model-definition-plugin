/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */


package org.jenkinsci.plugins.pipeline.modeldefinition.agent.impl

import org.jenkinsci.plugins.pipeline.modeldefinition.SyntheticStageNames
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import org.jenkinsci.plugins.workflow.cps.CpsScript
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

class DockerPipelineFromDockerfileScript extends AbstractDockerPipelineScript<DockerPipelineFromDockerfile> {

    DockerPipelineFromDockerfileScript(CpsScript s, DockerPipelineFromDockerfile a) {
        super(s, a)
    }

    @Override
    Closure runImage(Closure body) {
        return {
            def img = null
            if (!Utils.withinAStage()) {
                script.stage(SyntheticStageNames.agentSetup()) {
                    try {
                        img = buildImage().call()
                    } catch (Exception e) {
                        Utils.markStageFailedAndContinued(SyntheticStageNames.agentSetup())
                        throw e
                    }
                }
            } else {
                img = buildImage().call()
            }
            if (img != null) {
                img.inside(describable.args, {
                    body.call()
                })
            }
        }
    }

    private Closure buildImage() {
        return {
            def dockerfilePath = describable.getDockerfilePath(script.isUnix())
            try {
                RunWrapper runWrapper = (RunWrapper)script.getProperty("currentBuild")
                def additionalBuildArgs = describable.getAdditionalBuildArgs() ? " ${describable.additionalBuildArgs}" : ""
                def hash = Utils.stringToSHA1("${runWrapper.fullProjectName}\n${script.readFile("${dockerfilePath}")}\n${additionalBuildArgs}")
                def imgName = "${hash}"
                def commandLine = "docker build -t ${imgName}${additionalBuildArgs} -f \"${dockerfilePath}\" \"${describable.getActualDir()}\""
                script.sh commandLine
                script.dockerFingerprintFrom dockerfile: dockerfilePath, image: imgName, toolName: script.env.DOCKER_TOOL_NAME, commandLine: commandLine
                return script.getProperty("docker").image(imgName)
            } catch (FileNotFoundException f) {
                script.error("No Dockerfile found at ${dockerfilePath} in repository - failing.")
                return null
            }
        }
    }
}
