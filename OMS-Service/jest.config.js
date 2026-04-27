/** @type {import('jest').Config} */
const config = {
  //   preset: 'ts-jest',
  injectGlobals: true,
  testEnvironment: 'node',
  testMatch: ['**/test/**/*test.js'],
  verbose: true,
  forceExit: true,
  collectCoverage: true,
  coverageReporters: ['json', 'html'],
  clearMocks: true,
  resetMocks: true,
  setupFiles: ['<rootDir>/test/setup.js'],
  reporters: [
    'default', // keep the default reporter
    [
      'jest-xunit',
      {
        traitsRegex: [
          { regex: /\(Test Type:([^,)]+)(,|\)).*/g, name: 'Category' },

          { regex: /.*Test Traits: ([^)]+)\).*/g, name: 'Type' }
        ]
      }
    ],

    [
      'jest-html-reporters',

      {
        publicPath: './html-report',

        filename: 'report.html'
      }
    ]
  ]
};

module.exports = config;
