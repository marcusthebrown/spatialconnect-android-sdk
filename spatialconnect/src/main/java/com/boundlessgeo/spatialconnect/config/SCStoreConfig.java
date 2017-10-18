/**
 * Copyright 2015-2017 Boundless, http://boundlessgeo.com
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
 * See the License for the specific language governing permissions and limitations under the License
 */
package com.boundlessgeo.spatialconnect.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * Represents a JSON object describing a SCSpatialStore.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SCStoreConfig
{
    /**
     * The store's type.  geopackage, geojson, WMS, etc
     */
    @JsonProperty("store_type")
    private String type;

    /**
     * The version of the type. For example, 1.1.0 could be a valid version for the WMS type.
     */
    private String version;

    /**
     * The URI to the store.  Could be a file path or an HTTP(S) URL
     */
    private String uri;

    /**
     * Unique id of the store.
     */
    @JsonProperty("id")
    private String uniqueID;

    /**
     * Descriptive name for the store used in the UI display.  Can't have a period (.)
     */
    private String name;

    /**
     * Optionally specify default layers for this store.
     */
    @JsonProperty("default_layers")
    private List<String> defaultLayers;

    /**
     * Unique id of the team to which this store belongs.
     */
    @JsonProperty("team_id")
    private String teamId;

    /**
     * Represents the map styles for store
     */
    @JsonProperty("style")
    private ArrayNode style;

    /**
     * A map of options for this store.
     */
    @JsonProperty("options")
    private ObjectNode options;

    public SCStoreConfig() {}

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getVersion()
    {
        return (version != null) ? version : "1";
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    public String getUri()
    {
        return uri;
    }

    public void setUri(String uri)
    {
        this.uri = uri;
    }

    public String getUniqueID()
    {
        return uniqueID;
    }

    public void setUniqueID(String uniqueID)
    {
        this.uniqueID = uniqueID;
    }

    public String getName()
    {
        return name;
    }

    /** Cant have a period **/
    public void setName(String name)
    {
        this.name = name;
    }

    public List<String> getDefaultLayers() {
        return defaultLayers;
    }

    public void setDefaultLayers(List<String> defaultLayers) {
        this.defaultLayers = defaultLayers;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public ObjectNode getOptions() {
        return options;
    }

    public void setOptions(ObjectNode options) {
        this.options = options;
    }

    public ArrayNode getStyle() {
        return style;
    }

    public void setStyle(ArrayNode style) {
        this.style = style;
    }
}
