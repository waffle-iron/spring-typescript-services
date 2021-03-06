/**
 * Copyright © 2016 Mathias Kowalzik (Mathias.Kowalzik@leandreck.org)
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
package org.leandreck.endpoints.examples;

import org.leandreck.endpoints.annotations.TypeScriptEndpoint;
import org.leandreck.endpoints.annotations.TypeScriptIgnore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

/**
 * Created by Mathias Kowalzik (Mathias.Kowalzik@leandreck.org) on 19.08.2016.
 */
@TypeScriptEndpoint
@RestController
@RequestMapping("/api")
public class TestTypeScriptEndpoint {

    @RequestMapping(value = "/persons", method = GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public
    @ResponseBody
    List<RootType> getPersons() {
        final List<RootType> rootTypes = new ArrayList<>();
        rootTypes.add(new RootType());
        return rootTypes;
    }

    @RequestMapping(value = "/person/{id}", method = GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public
    @ResponseBody
    RootType getPerson(@PathVariable Long id) {
        return new RootType(id);
    }

    @RequestMapping(value = "/person/{id}", method = POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public
    @ResponseBody
    RootType updatePerson(@PathVariable Long id, @RequestBody RootType rootType) {
        rootType.setId(id);
        return rootType;
    }

    @RequestMapping(value = "/maps", method = GET, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public
    @ResponseBody
    Map<String, RootType> maps() {
        final Map<String, RootType> map = new HashMap<>();
        map.put("one", new RootType());
        return map;
    }

    @TypeScriptIgnore
    @RequestMapping(value = "/photos/{id}", method = GET, produces = MediaType.IMAGE_PNG_VALUE)
    public
    @ResponseBody
    ResponseEntity<InputStreamResource> getPhoto(@PathVariable Long id) {
        return ResponseEntity
                .ok()
                .contentLength(0)
                .contentType(MediaType.IMAGE_PNG)
                .body(new InputStreamResource(new ByteArrayInputStream("No Content".getBytes())));
    }

    private List<RootType> ignoreList() {
        return null;
    }

    protected List<RootType> ignoredTwo() {
        return null;
    }

    List<RootType> ignoredAlso() {
        return null;
    }
}
