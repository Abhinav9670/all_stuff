FROM node:20.14.0-alpine
WORKDIR /app
COPY package.json /app/package.json

RUN yarn
COPY . /app
ENV NODE_ENV production

USER node

CMD ["yarn", "start:prod"]

EXPOSE 8080
