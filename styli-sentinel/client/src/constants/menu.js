import { adminRoot } from './defaultValues';

const data = [
  {
    id: 'gogo',
    icon: 'iconsminds-forest-1',
    label: 'Services',
    to: `${adminRoot}/home`,
  },
  {
    id: 'secondmenu',
    icon: 'iconsminds-check',
    label: 'Roles',
    to: `${adminRoot}/roles`,
  },
  {
    id: 'blankpage',
    icon: 'iconsminds-business-man-woman',
    label: 'Users',
    to: `${adminRoot}/users`,
  },
  {
    id: 'blank',
    icon: 'iconsminds-check',
    label: 'Login History',
    to: `${adminRoot}/login-history`,
  },
  // {
  //   id: 'docs',
  //   icon: 'iconsminds-library',
  //   label: 'menu.docs',
  //   to: 'https://gogo-react-docs.coloredstrategies.com/',
  //   newWindow: true,
  // },
];
export default data;
