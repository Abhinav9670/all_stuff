package org.styli.services.order.pojo.order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * Created on 21-Nov-2022
 *
 * @author Piyush Kumar <piyush.kumar1@landmarkgroup.com>
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BreakDownItem {
    private String label;
    private String type;
    private String pattern;
    private String value;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BreakDownItem that = (BreakDownItem) o;
        return Objects.equals(type, that.type);
    }

    @Override
    public String toString() {
        return "BreakDownItem{" +
                "label='" + label + '\'' +
                ", type='" + type + '\'' +
                ", pattern='" + pattern + '\'' +
                ", value='" + value + '\'' +
                '}';
    }

    @Override
    public int hashCode() {
        int result = label != null ? label.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (pattern != null ? pattern.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }
}
