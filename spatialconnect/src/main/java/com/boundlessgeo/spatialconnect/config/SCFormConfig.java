package com.boundlessgeo.spatialconnect.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The <code>SCFormConfig</code> class represents the JSON configuration for a form in the context
 * of spatialconnect-server.  It defines the schema used by
 * <a href="https://github.com/boundlessgeo/spatialconnect-form-schema/">spatialconnect-form-schema
 * </a> to render a form on the presentation layer.  It is also used by the
 * {@link com.boundlessgeo.spatialconnect.stores.FormStore} to create a GeoPackage vector feature
 * table.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SCFormConfig {

    /**
     * Unique id of the form.
     */
    @JsonProperty("id")
    private String id;

    /**
     * Immutable name of the form.
     */
    @JsonProperty("form_key")
    private String formKey;

    /**
     * The label to display for the form.
     */
    @JsonProperty("form_label")
    private String formLabel;

    /**
     * The version of this form.
     */
    @JsonProperty("version")
    private String version;

    /**
     * List of the form fields that define this form.
     */
    @JsonProperty("fields")
    private List<HashMap<String, Object>> fields;

    /**
     * Unique id of the team to which this form belongs.
     */
    @JsonProperty("team_id")
    private String teamId;

    /**
     * Metadata about this form config.
     */
    @JsonProperty("metadata")
    private FormMetadata metadata;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<HashMap<String, Object>> getFields() {
        return fields;
    }

    public void setFields(List<HashMap<String, Object>> fields) {
        this.fields = fields;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getFormKey() {
        return formKey;
    }

    public void setFormKey(String formKey) {
        this.formKey = formKey;
    }

    public String getFormLabel() {
        return formLabel;
    }

    public void setFormLabel(String formLabel) {
        this.formLabel = formLabel;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public FormMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(FormMetadata metadata) {
        this.metadata = metadata;
    }

    public HashMap<String, Object> toJSON() {
        HashMap<String, Object> json = new HashMap<>();

        json.put("id", getId());
        json.put("form_key", getFormKey());  // same as layer name
        json.put("form_label", getFormLabel());
        json.put("version", getVersion());
        json.put("fields", getFields());

        return json;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SCFormConfig that = (SCFormConfig) o;

        return !(id != null ? !id.equals(that.id) : that.id != null);

    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    public class FormMetadata {

        private Integer count;

        @JsonProperty("lastActivity")
        private String lastActivity;

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public String getLastActivity() {
            return lastActivity;
        }

        public void setLastActivity(String lastActivity) {
            this.lastActivity = lastActivity;
        }
    }
}


