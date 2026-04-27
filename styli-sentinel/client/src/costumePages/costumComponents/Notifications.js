import { NotificationManager } from '../../components/common/react-notifications';

export const createNotification = ({ type, className, title, subtitle }) => {
  const cName = className || '';
  switch (type) {
    case 'primary':
      NotificationManager.primary(subtitle, title, 3000, null, null, cName);
      break;
    case 'secondary':
      NotificationManager.secondary(subtitle, title, 3000, null, null, cName);
      break;
    case 'info':
      NotificationManager.info('Info message', '', 3000, null, null, cName);
      break;
    case 'success':
      NotificationManager.success(subtitle, title, 3000, null, null, cName);
      break;
    case 'warning':
      NotificationManager.warning(subtitle, title, 3000, null, null, cName);
      break;
    case 'error':
      NotificationManager.error(
        subtitle,
        title,
        5000,
        () => {
          alert('callback');
        },
        null,
        cName
      );
      break;
    default:
      NotificationManager.info('Info message');
      break;
  }
};
