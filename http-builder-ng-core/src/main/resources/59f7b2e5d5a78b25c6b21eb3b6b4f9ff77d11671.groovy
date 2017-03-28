/*
 * Copyright (C) 2017 David Clark
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

import groovyx.net.http.NativeHandlers

import java.nio.charset.StandardCharsets

request.with {
    charset = StandardCharsets.UTF_8
    encoder BINARY, NativeHandlers.Encoders.&binary
    encoder TEXT, NativeHandlers.Encoders.&text
    encoder URLENC, NativeHandlers.Encoders.&form
    encoder XML, NativeHandlers.Encoders.&xml
    encoder JSON, NativeHandlers.Encoders.&json
}

response.with {
    success NativeHandlers.&success
    failure NativeHandlers.&failure
    exception NativeHandlers.&exception
    
    parser BINARY, NativeHandlers.Parsers.&streamToBytes
    parser TEXT, NativeHandlers.Parsers.&textToString
    parser URLENC, NativeHandlers.Parsers.&form
    parser XML, NativeHandlers.Parsers.&xml
    parser JSON, NativeHandlers.Parsers.&json
}
