import React, { useEffect, useState } from 'react';
import {
  Card,
  CardBody,
  CardText,
  Button,
  CardSubtitle,
  Input,
  Row,
} from 'reactstrap';
import { Colxx } from '../../components/common/CustomBootstrap';
import CostumForm from './costumForm';
import DeleteModal from './DeleteModal';

export default function EditCard({
  data = {},
  setData = () => {},
  getResponse = () => {},
  formType = '',
  setFormType = '',
  handleDelete = () => {},
  domain = '',
}) {
  const [openDeleteModal, setOpenDeleteModal] = useState(false);

  const { name = '', url = '', httpMethod = '' } = data;

  return (
    <div>
      <Card className="mt-2">
        <div className="position-relative"></div>
        <div className="pl-4 pr-4 pt-4 justifyContent-cen alignItm-cen">
          <h2>{data.name ? name : 'Create End Point'}</h2>
          <CardSubtitle>{`${httpMethod} ${url}`}</CardSubtitle>
        </div>
        <CardBody>
          <CardText className="text-muted text-small mb-0 font-weight-light ">
            {data && (
              <CostumForm editData={data} setData={setData} domain={domain} />
            )}

            <div className="text-right d-flex">
              {formType !== 'create' && (
                <Button
                  onClick={() => setData({})}
                  className="ml-0 mr-auto"
                  outline
                >
                  Add New
                </Button>
              )}
              {formType !== 'create' && (
                <Button
                  onClick={() => setOpenDeleteModal(true)}
                  className="ml-auto"
                  color="danger"
                >
                  Delete
                </Button>
              )}
              <Button
                onClick={() =>
                  getResponse(formType === 'create' ? 'create' : 'save')
                }
                className={formType === 'create' ? 'ml-auto' : 'ml-1'}
              >
                {formType === 'create' ? 'Create' : 'Save'}
              </Button>
            </div>
          </CardText>
        </CardBody>
      </Card>
      {openDeleteModal && (
        <DeleteModal
          openDeleteModal={openDeleteModal}
          setOpenDeleteModal={setOpenDeleteModal}
          response={(resp) => {
            resp ? handleDelete() : '';
          }}
        />
      )}
    </div>
  );
}
