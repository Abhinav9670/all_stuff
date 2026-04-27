module.exports = {
  env: {
    es2021: true,
    node: true
  },
  plugins: ['prettier'],
  extends: ['eslint:recommended', 'plugin:prettier/recommended', 'styli'],
  parserOptions: {
    ecmaVersion: 12
  },
  rules: {
    indent: ['error', 2, { SwitchCase: 1 }],
    'linebreak-style': ['error', 'unix'],
    quotes: ['error', 'single'],
    semi: ['error', 'always'],
    'no-async-promise-executor': 'off'
  }
};
