FROM node:20-alpine

RUN apk add curl

# Create and change to the app directory.
WORKDIR /usr/src/app
ENV PORT 5001
# ENV NODE_ENV production
ENV HOST 0.0.0.0
RUN npm i -g pm2


#ARG NODE_ENV
#ENV NODE_ENV $NODE_ENV

RUN mkdir /app
WORKDIR /app
ADD package.json /app/
#RUN yarn --pure-lockfile
ADD . /app

COPY package*.json ./

RUN yarn install
COPY . /usr/src/app

CMD ["yarn", "start"]
EXPOSE 5001
