/*
 * Copyright (C) 2017 HttpBuilder-NG Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package groovyx.net.http

import com.stehno.ersatz.Encoders
import com.stehno.ersatz.ErsatzServer
import groovyx.net.http.optional.Download
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

import static com.stehno.ersatz.ContentType.TEXT_JSON
import static com.stehno.ersatz.ContentType.TEXT_PLAIN
import static groovyx.net.http.UriBuilder.basic
import static groovyx.net.http.UriBuilder.root
import static groovyx.net.http.util.SslUtils.ignoreSslIssues

class UriBuilderSpec extends Specification {

    @Rule TemporaryFolder folder = new TemporaryFolder()

    def 'root yields empty URI'() {
        expect:
        root().toURI() == new URI('')
    }

    def 'builder with parent'() {
        given:
        def builderA = basic(root()).setFull('http://localhost:8080/info')

        expect:
        basic(builderA).toURI() == new URI('http://localhost:8080/info')

        and:
        basic(builderA).setPath('/bar').toURI() == new URI('http://localhost:8080/bar')
    }

    def 'basic from root'() {
        given:
        UriBuilder builder = basic(root())

        expect:
        builder.toURI() == new URI('')

        and:
        builder.setFull('http://localhost:10101').toURI() == new URI('http://localhost:10101')

        and:
        builder.setPath('/foo').toURI() == new URI('http://localhost:10101/foo')

        and:
        builder.setScheme('https').toURI() == new URI('https://localhost:10101/foo')

        and:
        builder.setPort(9191).toURI() == new URI('https://localhost:9191/foo')

        and:
        builder.setHost('nohost').toURI() == new URI('https://nohost:9191/foo')

        and:
        builder.setQuery(alpha: '1', bravo: 100).toURI() == new URI('https://nohost:9191/foo?alpha=1&bravo=100')

        and:
        builder.setQuery(alpha: '1', bravo: [100, 200]).toURI() == new URI('https://nohost:9191/foo?alpha=1&bravo=100&bravo=200')

        and:
        builder.setFragment('horse').toURI() == new URI('https://nohost:9191/foo?alpha=1&bravo=100&bravo=200#horse')

        and:
        builder.setUserInfo('dog').toURI() == new URI('https://dog@nohost:9191/foo?alpha=1&bravo=100&bravo=200#horse')
    }

    def 'threadSafe from root'() {
        given:
        UriBuilder builder = UriBuilder.threadSafe(root())

        expect:
        builder.toURI() == new URI('')

        and:
        builder.setFull('http://localhost:10101').toURI() == new URI('http://localhost:10101')

        and:
        builder.setPath('/foo').toURI() == new URI('http://localhost:10101/foo')

        and:
        builder.setScheme('https').toURI() == new URI('https://localhost:10101/foo')

        and:
        builder.setPort(9191).toURI() == new URI('https://localhost:9191/foo')

        and:
        builder.setHost('nohost').toURI() == new URI('https://nohost:9191/foo')

        and:
        builder.setQuery(alpha: '1', bravo: 100).toURI() == new URI('https://nohost:9191/foo?bravo=100&alpha=1')

        and:
        builder.setQuery(alpha: '1', bravo: [100, 300]).toURI() == new URI('https://nohost:9191/foo?bravo=100&bravo=300&alpha=1')

        and:
        builder.setFragment('horse').toURI() == new URI('https://nohost:9191/foo?bravo=100&bravo=300&alpha=1#horse')

        and:
        builder.setUserInfo('dog').toURI() == new URI('https://dog@nohost:9191/foo?bravo=100&bravo=300&alpha=1#horse')
    }

    def 'commas allowed in query string'() {
        given:
        UriBuilder tsafe = UriBuilder.threadSafe(root())
        UriBuilder tunsafe = basic(root())

        expect:
        tsafe.setFull('http://foo.com/endpoint').setQuery(ids: '1,2,3').toURI() == new URI('http://foo.com/endpoint?ids=1,2,3')
        tunsafe.setFull('http://foo.com/endpoint').setQuery(ids: '1,2,3').toURI() == new URI('http://foo.com/endpoint?ids=1,2,3')
    }

    def 'full uri with path'() {
        setup:
        UriBuilder builder = basic(root())

        when:
        builder.full = 'http://localhost:1234/something/else'

        then:
        builder.toURI() == 'http://localhost:1234/something/else'.toURI()

        when:
        builder.path = '/foo'

        then:
        builder.toURI() == 'http://localhost:1234/foo'.toURI()
    }

    def 'uri full specified with query string'() {
        setup:
        UriBuilder builder = basic(root())

        when:
        builder.full = 'http://test.com/a?b=c&d=e'
        URI uri = builder.toURI()

        then:
        uri == 'http://test.com/a?b=c&d=e'.toURI()
    }

    def 'uri full specified with duplicates in query string'() {
        setup:
        UriBuilder builder = basic(root())

        when:
        builder.full = 'http://test.com/a?b=c&d=e&b=f'
        URI uri = builder.toURI()

        then:
        uri == 'http://test.com/a?b=c&b=f&d=e'.toURI()
    }

    def 'uri with query in configuration and empty verb'() {
        setup:
        def server = new ErsatzServer({
            encoder TEXT_PLAIN, String, Encoders.text
            expects().get('/something').query('foo', 'bar').responds().content('ok', TEXT_PLAIN)
        })
        server.start()

        when:
        def http = JavaHttpBuilder.configure {
            request.uri = "${server.httpUrl}/something?foo=bar"
        }.get()

        then:
        http == 'ok'

        cleanup:
        server.stop()
    }

    def 'url with encoded slash'() {
        setup:
        String raw = 'http://localhost:8181/api/v4/projects/myteam%2Fmyrepo/repository/files/myfile.json?ref=master'

        UriBuilder builder = basic(root())
        builder.useRawValues = true

        when:
        builder.full = raw
        URI uri = builder.toURI()

        then:
        uri == raw.toURI()
        uri.rawPath == '/api/v4/projects/myteam%2Fmyrepo/repository/files/myfile.json'
    }

    def 'url with encoded query'() {
        setup:
        String raw = 'http://localhost:8181/api/v4/projects/myteam%2Fmyrepo/repository/files/myfile.json?ref=master%2Fone&alpha=bravo'

        UriBuilder builder = basic(root())
        builder.useRawValues = true

        when:
        builder.full = raw
        URI uri = builder.toURI()

        then:
        uri == raw.toURI()
        uri.rawPath == '/api/v4/projects/myteam%2Fmyrepo/repository/files/myfile.json'
        uri.rawQuery == 'ref=master%2Fone&alpha=bravo'
    }

    def 'url with encoded slash (2)'() {
        setup:
        def server = new ErsatzServer({
            expectations {
                get('/api/v4/projects/myteam%2Fmyrepo/repository/files/myfile.json').responds().code(200).content('ok', TEXT_PLAIN)
            }
        })

        when:
        def result = JavaHttpBuilder.configure {
            request.raw = "${server.httpUrl}/api/v4/projects/myteam%2Fmyrepo/repository/files/myfile.json"
        }.get()

        then:
        result == 'ok'
    }

    def 'url with encoded slash (3)'() {
        setup:
        def server = new ErsatzServer({
            expectations {
                get('/api/v4/projects/myteam%2Fmyrepo/repository/files/myfile.json').responds().code(200).content('ok', TEXT_PLAIN)
            }
        })

        when:
        def result = JavaHttpBuilder.configure {
            request.raw = server.httpUrl
        }.get {
            request.uri.path = '/api/v4/projects/myteam%2Fmyrepo/repository/files/myfile.json'
        }

        then:
        result == 'ok'
    }

    def 'another encoded uri test'() {
        setup:
        String apiNamespace = 'something'
        String apiRepoName = 'somewhere'
        String gitFilePath = 'thefile'
        String apiToken = 'asdfasdfasdf'

        File dir = folder.newFolder()

        def server = new ErsatzServer({
            https()
            expectations {
                get("/api/v4/projects/${apiNamespace}%2F${apiRepoName}/repository/files/${gitFilePath}/salt-api_request.json") {
                    query 'ref', 'master'
                    protocol 'HTTPS'
                    called 1
                    responder {
                        code 200
                        content '{"value":"ok"}', TEXT_JSON
                    }
                }
            }
        })

        when:
        boolean hasFailure = false

        JavaHttpBuilder.configure {
            request.raw = "${server.httpsUrl}/api/v4/projects/${apiNamespace}%2F${apiRepoName}/repository/files/${gitFilePath}/salt-api_request.json?ref=master"
            request.contentType = 'application/json'
            request.accept = 'application/json'
            request.headers['PRIVATE-TOKEN'] = apiToken

            ignoreSslIssues execution

            response.failure { FromServer fs, Object body ->
                hasFailure = true
            }

        }.get {
            Download.toFile(delegate as HttpConfig, new File(dir, 'salt-api_request.json'))
        }

        then: 'there is no failure'
        !hasFailure

        and: 'the file has the expected content'
        def file = new File(dir, 'salt-api_request.json')
        file.exists()
        file.text == '{"value":"ok"}'

        and: 'the server was actually called'
        server.verify()
    }

    def 'uri with param with no value'() {
        setup:
        UriBuilder builder = basic(root())

        when:
        builder.full = 'http://test.com/a?wsdl'
        URI uri = builder.toURI()

        then:
        uri == 'http://test.com/a?wsdl'.toURI()
    }

    @Unroll 'relative paths (#baseUrl #path -> #fullUrl)'() {
        setup:
        UriBuilder builder = basic(root())
        builder.full = 'http://localhost:9191/something/'

        when:
        builder.path = path
        URI uri = builder.toURI()

        then:
        uri == fullUrl.toURI()

        where:
        baseUrl                            | path          || fullUrl
        'http://localhost:9191/something/' | 'more/pathy'  || 'http://localhost:9191/something/more/pathy'
        'http://localhost:9191/something'  | 'more/pathy'  || 'http://localhost:9191/something/more/pathy'
        'http://localhost:9191/something/' | '/more/pathy' || 'http://localhost:9191/more/pathy'
        'http://localhost:9191/something'  | '/more/pathy' || 'http://localhost:9191/more/pathy'
    }

    @Issue('#210')
    def 'relative paths should work with GSting'() {
        when: 'path casted to java.lang.String'
        URI withString = basic(root())
                .setFull("http://localhost:9191/something/")
                .setPath("more/pathy")
                .toURI()

        then:
        withString == 'http://localhost:9191/something/more/pathy'.toURI()

        when: 'past as GString'
        String pathy = 'pathy'
        URI withGString = basic(root())
                .setFull("http://localhost:9191/something/")
                .setPath("more/$pathy")
                .toURI()

        then:
        withGString == 'http://localhost:9191/something/more/pathy'.toURI()
    }
}
