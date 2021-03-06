package org.opencb.opencga.catalog.models.update;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.opencb.biodata.models.clinical.interpretation.Comment;
import org.opencb.biodata.models.clinical.interpretation.DiseasePanel;
import org.opencb.biodata.models.clinical.interpretation.ReportedLowCoverage;
import org.opencb.biodata.models.clinical.interpretation.ReportedVariant;
import org.opencb.biodata.models.commons.Analyst;
import org.opencb.biodata.models.commons.Software;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.opencga.catalog.exceptions.CatalogException;

import java.util.List;
import java.util.Map;

import static org.opencb.opencga.core.common.JacksonUtils.getUpdateObjectMapper;

public class InterpretationUpdateParams {

    private String id;
    private String description;
    private String clinicalAnalysisId;
    private List<DiseasePanel> panels;
    private Software software;
    private Analyst analyst;
    private List<Software> dependencies;
    private Map<String, Object> filters;
    private String creationDate;
    private List<ReportedVariant> primaryFindings;
    private List<ReportedVariant> secondaryFindings;
    private List<ReportedLowCoverage> reportedLowCoverages;
    private List<Comment> comments;
    private Map<String, Object> attributes;

    public InterpretationUpdateParams() {
    }

    public ObjectMap getUpdateMap() throws CatalogException {
        try {
            return new ObjectMap(getUpdateObjectMapper().writeValueAsString(this));
        } catch (JsonProcessingException e) {
            throw new CatalogException(e);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("InterpretationUpdateParams{");
        sb.append("id='").append(id).append('\'');
        sb.append(", description='").append(description).append('\'');
        sb.append(", clinicalAnalysisId='").append(clinicalAnalysisId).append('\'');
        sb.append(", panels=").append(panels);
        sb.append(", software=").append(software);
        sb.append(", analyst=").append(analyst);
        sb.append(", dependencies=").append(dependencies);
        sb.append(", filters=").append(filters);
        sb.append(", creationDate='").append(creationDate).append('\'');
        sb.append(", primaryFindings=").append(primaryFindings);
        sb.append(", secondaryFindings=").append(secondaryFindings);
        sb.append(", reportedLowCoverages=").append(reportedLowCoverages);
        sb.append(", comments=").append(comments);
        sb.append(", attributes=").append(attributes);
        sb.append('}');
        return sb.toString();
    }

    public String getId() {
        return id;
    }

    public InterpretationUpdateParams setId(String id) {
        this.id = id;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public InterpretationUpdateParams setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getClinicalAnalysisId() {
        return clinicalAnalysisId;
    }

    public InterpretationUpdateParams setClinicalAnalysisId(String clinicalAnalysisId) {
        this.clinicalAnalysisId = clinicalAnalysisId;
        return this;
    }

    public List<DiseasePanel> getPanels() {
        return panels;
    }

    public InterpretationUpdateParams setPanels(List<DiseasePanel> panels) {
        this.panels = panels;
        return this;
    }

    public Software getSoftware() {
        return software;
    }

    public InterpretationUpdateParams setSoftware(Software software) {
        this.software = software;
        return this;
    }

    public Analyst getAnalyst() {
        return analyst;
    }

    public InterpretationUpdateParams setAnalyst(Analyst analyst) {
        this.analyst = analyst;
        return this;
    }

    public List<Software> getDependencies() {
        return dependencies;
    }

    public InterpretationUpdateParams setDependencies(List<Software> dependencies) {
        this.dependencies = dependencies;
        return this;
    }

    public Map<String, Object> getFilters() {
        return filters;
    }

    public InterpretationUpdateParams setFilters(Map<String, Object> filters) {
        this.filters = filters;
        return this;
    }

    public String getCreationDate() {
        return creationDate;
    }

    public InterpretationUpdateParams setCreationDate(String creationDate) {
        this.creationDate = creationDate;
        return this;
    }

    public List<ReportedVariant> getPrimaryFindings() {
        return primaryFindings;
    }

    public InterpretationUpdateParams setPrimaryFindings(List<ReportedVariant> primaryFindings) {
        this.primaryFindings = primaryFindings;
        return this;
    }

    public List<ReportedVariant> getSecondaryFindings() {
        return secondaryFindings;
    }

    public InterpretationUpdateParams setSecondaryFindings(List<ReportedVariant> secondaryFindings) {
        this.secondaryFindings = secondaryFindings;
        return this;
    }

    public List<ReportedLowCoverage> getReportedLowCoverages() {
        return reportedLowCoverages;
    }

    public InterpretationUpdateParams setReportedLowCoverages(List<ReportedLowCoverage> reportedLowCoverages) {
        this.reportedLowCoverages = reportedLowCoverages;
        return this;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public InterpretationUpdateParams setComments(List<Comment> comments) {
        this.comments = comments;
        return this;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public InterpretationUpdateParams setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
        return this;
    }
}
