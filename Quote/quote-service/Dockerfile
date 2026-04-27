FROM node:20.14-alpine
WORKDIR /app
COPY package.json /app/package.json
# RUN apk add --update python make g++\
#    && rm -rf /var/cache/apk/*
RUN apk add g++ gcc libgcc libstdc++ linux-headers make python3 curl

RUN yarn install
COPY . /app
# Create heap-snapshots directory with write permissions for node user
# Must be done before switching to node user
RUN mkdir -p /app/heap-snapshots && \
    chown node:node /app/heap-snapshots && \
    chmod 775 /app/heap-snapshots
ENV NODE_ENV qa
ENV NODE_OPTIONS="--max-old-space-size=1200"

USER node
## use node instead of npm/yarn
##CMD ["node", "server.js", "--production"]

## start with pm2
CMD ["yarn", "start:prod"]

EXPOSE 5000
