FROM grafana/grafana:12.1.0

ARG VICTORIALOGS_PLUGIN_VERSION=0.33.1
ENV GF_PATHS_PLUGINS=/var/lib/grafana-plugins

USER root
RUN mkdir -p "$GF_PATHS_PLUGINS" \
    && chown 472:0 "$GF_PATHS_PLUGINS"

USER 472
RUN grafana cli --pluginsDir "$GF_PATHS_PLUGINS" plugins install \
    victoriametrics-logs-datasource "$VICTORIALOGS_PLUGIN_VERSION"
