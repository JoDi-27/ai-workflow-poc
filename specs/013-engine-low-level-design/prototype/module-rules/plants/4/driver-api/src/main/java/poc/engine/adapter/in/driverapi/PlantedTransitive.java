package poc.engine.adapter.in.driverapi;

import org.springframework.util.StringUtils;

class PlantedTransitive {
    boolean blank(String s) { return !StringUtils.hasText(s); }
}
