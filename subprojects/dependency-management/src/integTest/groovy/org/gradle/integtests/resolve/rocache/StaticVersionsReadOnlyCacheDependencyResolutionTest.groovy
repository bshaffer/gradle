/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.integtests.resolve.rocache

import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import spock.lang.Unroll

class StaticVersionsReadOnlyCacheDependencyResolutionTest extends AbstractReadOnlyCacheDependencyResolutionTest {

    def "fetches dependencies from read-only cache"() {
        given:
        buildFile << """
            dependencies {
                implementation 'org.readonly:core:1.0'
            }
        """

        when:
        withReadOnlyCache()
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', 'org.gradle:ro-test:20') {
                module('org.readonly:core:1.0')
            }
        }

        and:
        assertInReadOnlyCache('core-1.0.jar')
    }

    def "missing dependencies are added to writable cache"() {
        given:
        def other = mavenHttpRepo.module('org.other', 'other', '1.0').withModuleMetadata().publish()
        buildFile << """
            dependencies {
                implementation 'org.readonly:core:1.0'
                implementation 'org.other:other:1.0'
            }
        """

        when:
        withReadOnlyCache()

        other.pom.expectGet()
        other.moduleMetadata.expectGet()
        other.artifact.expectGet()

        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', 'org.gradle:ro-test:20') {
                module('org.readonly:core:1.0')
                module('org.other:other:1.0')
            }
        }

        and:
        assertInReadOnlyCache('core-1.0.jar')
        assertNotInReadOnlyCache("other-1.0.jar")
    }

    @Unroll
    def "can recover from corrupt read-only cache (#file)"() {
        given:
        def core = mavenHttpRepo.module('org.readonly', 'core', '1.0')
        buildFile << """
            dependencies {
                implementation 'org.readonly:core:1.0'
            }
        """

        when:
        fileInReadReadOnlyCache("modules-${CacheLayout.ROOT.version}/metadata-${CacheLayout.META_DATA.version}/${file}.bin").bytes = [0, 0, 0]
        withReadOnlyCache()

        core.allowAll()

        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', 'org.gradle:ro-test:20') {
                module('org.readonly:core:1.0')
            }
        }

        where:
        file << [
            'module-metadata',
            'module-artifact',
            'module-artifacts',
            'resource-at-url'
        ]
    }


    @Override
    List<MavenHttpModule> getModulesInReadOnlyCache(MavenHttpRepository repo) {
        [
            repo.module("org.readonly", "core", "1.0"),
            repo.module("org.readonly", "util", "1.0")
        ]
    }
}
