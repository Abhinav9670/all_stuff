import {
  defaultDirection,
  defaultLocale,
  defaultColor,
  localeOptions,
  themeColorStorageKey,
  themeRadiusStorageKey
} from '../constants/defaultValues';

const subdomain = window.location.hostname;
const subdomainParts = subdomain.split('.');
const domainName = subdomainParts.slice(-2).join('.');

export const mapOrder = (array, order, key) => {
  // eslint-disable-next-line func-names
  array.sort(function (a, b) {
    const A = a[key];
    const B = b[key];
    if (order.indexOf(`${A}`) > order.indexOf(`${B}`)) {
      return 1;
    }
    return -1;
  });
  return array;
};

export const getDateWithFormat = () => {
  const today = new Date();
  let dd = today.getDate();
  let mm = today.getMonth() + 1; // January is 0!

  const yyyy = today.getFullYear();
  if (dd < 10) {
    dd = `0${dd}`;
  }
  if (mm < 10) {
    mm = `0${mm}`;
  }
  return `${dd}.${mm}.${yyyy}`;
};

export const getCurrentTime = () => {
  const now = new Date();
  return `${now.getHours()}:${now.getMinutes()}`;
};

export const getDirection = () => {
  let direction = defaultDirection;

  try {
    if (localStorage.getItem('direction')) {
      const localValue = localStorage.getItem('direction');
      if (localValue === 'rtl' || localValue === 'ltr') {
        direction = localValue;
      }
    }
  } catch (error) {
    console.log('>>>>: src/helpers/Utils.js : getDirection -> error', error);
    direction = defaultDirection;
  }
  return {
    direction,
    isRtl: direction === 'rtl'
  };
};
export const setDirection = localValue => {
  let direction = 'ltr';
  if (localValue === 'rtl' || localValue === 'ltr') {
    direction = localValue;
  }
  try {
    localStorage.setItem('direction', direction);
  } catch (error) {
    console.log('>>>>: src/helpers/Utils.js : setDirection -> error', error);
  }
};

export const getCurrentColor = () => {
  let currentColor = defaultColor;
  try {
    if (localStorage.getItem(themeColorStorageKey)) {
      currentColor = localStorage.getItem(themeColorStorageKey);
    }
  } catch (error) {
    console.log('>>>>: src/helpers/Utils.js : getCurrentColor -> error', error);
    currentColor = defaultColor;
  }
  return currentColor;
};

export const setCurrentColor = color => {
  try {
    localStorage.setItem(themeColorStorageKey, color);
  } catch (error) {
    console.log('>>>>: src/helpers/Utils.js : setCurrentColor -> error', error);
  }
};

export const getCurrentRadius = () => {
  let currentRadius = 'rounded';
  try {
    if (localStorage.getItem(themeRadiusStorageKey)) {
      currentRadius = localStorage.getItem(themeRadiusStorageKey);
    }
  } catch (error) {
    console.log('>>>>: src/helpers/Utils.js : getCurrentRadius -> error', error);
    currentRadius = 'rounded';
  }
  return currentRadius;
};
export const setCurrentRadius = radius => {
  try {
    localStorage.setItem(themeRadiusStorageKey, radius);
  } catch (error) {
    console.log('>>>>: src/helpers/Utils.js : setCurrentRadius -> error', error);
  }
};

export const getCurrentLanguage = () => {
  let language = defaultLocale;
  try {
    language =
      localStorage.getItem('currentLanguage') &&
      localeOptions.filter(x => x.id === localStorage.getItem('currentLanguage')).length > 0
        ? localStorage.getItem('currentLanguage')
        : defaultLocale;
  } catch (error) {
    console.log('>>>>: src/helpers/Utils.js : getCurrentLanguage -> error', error);
    language = defaultLocale;
  }
  return language;
};
export const setCurrentLanguage = locale => {
  try {
    localStorage.setItem('currentLanguage', locale);
  } catch (error) {
    console.log('>>>>: src/helpers/Utils.js : setCurrentLanguage -> error', error);
  }
};

const getCookie = (cookieName, domain) => {
  const cookies = document.cookie.split(';');
  for (let i = 0; i < cookies.length; i++) {
    let cookie = cookies[i].trim();
    // Check if the cookie starts with the specified name
    if (cookie.indexOf(cookieName + '=') === 0) {
      // Check if the cookie domain matches the specified domain
      if (domain && cookie.indexOf('domain=' + domain) === -1) {
        return cookie.substring(cookieName.length + 1);
      }
    }
  }
  return null;
};
const getJsonCookie = (cookieName, domain) => {
  const cookieValue = getCookie(cookieName, domain);
  if (cookieValue) {
    try {
      // Parse the JSON value from the cookie
      return JSON.parse(decodeURIComponent(cookieValue));
    } catch (error) {
      // Handle parsing errors if necessary
      console.error('Error parsing JSON from cookie:', error);
    }
  }

  return null; // Return null if the cookie or JSON value doesn't exist
};

const setJsonCookie = (cookieName, jsonObject, expirationDays, domain) => {
  const jsonString = JSON.stringify(jsonObject);

  let cookieString = `${cookieName}=${encodeURIComponent(jsonString)}`;

  if (expirationDays) {
    const expirationDate = new Date();
    expirationDate.setDate(expirationDate.getDate() + expirationDays);
    cookieString += `; expires=${expirationDate.toUTCString()}`;
  }

  if (domain) {
    cookieString += `; domain=${domain}; path=/;`;
  }

  document.cookie = cookieString;
};

export const getCurrentUser = () => {
  let user = null;
  try {
    user = getJsonCookie('styli_sso_user', domainName);
  } catch (error) {
    console.log('>>>>: src/helpers/Utils.js  : getCurrentUser -> error', error);
    user = null;
  }
  return user;
};

export const setCurrentUser = user => {
  const cookieName = 'styli_sso_user';
  try {
    if (user) {
      setJsonCookie(cookieName, user, 0, domainName);
    } else {
      document.cookie = `${cookieName}=; domain=${domainName}; path=/;`;
    }
  } catch (error) {
    console.log('>>>>: src/helpers/Utils.js : setCurrentUser -> error', error);
  }
};

export const removeFromCookie = () => {
  const cookieName = 'styli_sso_user';
  document.cookie = `${cookieName}=; path=/; domain=${domainName};`; //added path to this removeCookie function
};
