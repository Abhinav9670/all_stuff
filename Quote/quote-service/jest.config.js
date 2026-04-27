/** @type {import('jest').Config} */
const config = {
  //   preset: 'ts-jest',
  injectGlobals: true,
  testEnvironment: 'node',
  testMatch: ['**/__tests__/**/*.test.js'],
  verbose: true,
  forceExit: true,
  collectCoverage: true,
  coverageReporters: ['json', 'html', 'text'],
  clearMocks: true,
  resetMocks: true,
  //   restoreMocks: true,
  setupFilesAfterEnv: ['./__tests__/settings/setupTests.js'],
  testTimeout: 90000,
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
