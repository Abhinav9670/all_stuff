require('dotenv-expand')(
  require('dotenv').config({
    path: require('find-config')(
      `.env.${process.env.NODE_ENV || 'development.local'}`
    )
  })
);
 
const {
  MYSQL_HOST,
  MYSQL_USER,
  MYSQL_PWD,
  MYSQL_DB,
  MYSQL_POOL_MIN,
  MYSQL_POOL_MAX,
  MYSQL_DB_PORT,
  ARCHIVE_MYSQL_DB,
  ARCHIVE_MYSQL_USER,
  ARCHIVE_MYSQL_PWD,
  ARCHIVE_MYSQL_HOST,
  ARCHIVE_MYSQL_DB_PORT
} = process.env;
 
const pool = {
  max: Number(MYSQL_POOL_MAX || 5),
  min: Number(MYSQL_POOL_MIN || 1)
};
module.exports = {
  development: {
    username: MYSQL_USER,
    password: MYSQL_PWD,
    database: MYSQL_DB,
    host: MYSQL_HOST,
    port:MYSQL_DB_PORT,
    dialect: 'mysql',
    pool,
    logging: false, // Disable SQL query logging
    dialectOptions: {
      ssl: {
        require: true,
        rejectUnauthorized: false
      }
    }
  },
  test: {
    username: MYSQL_USER,
    password: MYSQL_PWD,
    database: MYSQL_DB,
    host: MYSQL_HOST,
    dialect: 'mysql',
    pool,
    logging: false
  },
  production: {
    username: MYSQL_USER,
    password: MYSQL_PWD,
    database: MYSQL_DB,
    host: MYSQL_HOST,
    dialect: 'mysql',
    pool,
    logging: false,
    dialectOptions: {
      ssl: {
        require: true,
        rejectUnauthorized: false
      }
    }
  },
  uat: {
    username: MYSQL_USER,
    password: MYSQL_PWD,
    database: MYSQL_DB,
    host: MYSQL_HOST,
    dialect: 'mysql',
    pool,
    logging: false,
    dialectOptions: {
      ssl: {
        require: true,
        rejectUnauthorized: false
      }
    }
  },
  qa: {
    username: MYSQL_USER,
    password: MYSQL_PWD,
    database: MYSQL_DB,
    host: MYSQL_HOST,
    dialect: 'mysql',
    pool,
    logging: false,
    dialectOptions: {
      ssl: {
        require: true,
        rejectUnauthorized: false
      }
    }
  },
  archive: {
    username: ARCHIVE_MYSQL_USER,
    password: ARCHIVE_MYSQL_PWD,
    database: ARCHIVE_MYSQL_DB,
    host: ARCHIVE_MYSQL_HOST,
    port:ARCHIVE_MYSQL_DB_PORT,
    dialect: 'mysql',
    pool,
    logging: false,
    dialectOptions: {
      ssl: {
        require: true,
        rejectUnauthorized: false
      }
    }
  }
};
 
 