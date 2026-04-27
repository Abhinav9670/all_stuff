import React, { useState } from 'react';
import { DragDropContext, Draggable, Droppable } from 'react-beautiful-dnd';
import { Row, Card, CardBody } from 'reactstrap';
import { Colxx } from '../../components/common/CustomBootstrap';

const tasks = [
  { id: '1', content: 'First task' },
  { id: '2', content: 'Second task' },
  { id: '3', content: 'Third task' },
  { id: '4', content: 'Fourth task' },
  { id: '5', content: 'Fifth task' },
];

const taskStatus = {
  requested: {
    name: 'Role',
    items: [],
  },
  toDo: {
    name: 'To do',
    items: tasks,
  },
};

const onDragEnd = (result, columns, setColumns) => {
  if (!result.destination) return;
  const { source, destination } = result;

  if (source.droppableId !== destination.droppableId) {
    const sourceColumn = columns[source.droppableId];
    const destColumn = columns[destination.droppableId];
    const sourceItems = [...sourceColumn.items];
    const destItems = [...destColumn.items];
    const [removed] = sourceItems.splice(source.index, 1);
    destItems.splice(destination.index, 0, removed);
    setColumns({
      ...columns,
      [source.droppableId]: {
        ...sourceColumn,
        items: sourceItems,
      },
      [destination.droppableId]: {
        ...destColumn,
        items: destItems,
      },
    });
  } else {
    const column = columns[source.droppableId];
    const copiedItems = [...column.items];
    const [removed] = copiedItems.splice(source.index, 1);
    copiedItems.splice(destination.index, 0, removed);
    setColumns({
      ...columns,
      [source.droppableId]: {
        ...column,
        items: copiedItems,
      },
    });
  }
};

export default function DragDropComponent() {
  const [columns, setColumns] = useState(taskStatus);

  return (
    <div>
      <Row>
        <div
          style={{
            display: 'flex',
            justifyContent: 'center',
            height: 'max-content',
          }}
        >
          <DragDropContext
            onDragEnd={(result) => onDragEnd(result, columns, setColumns)}
          >
            {Object.entries(columns).map(([columnId, column], index) => {
              return (
                <div
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                  }}
                  key={columnId}
                >
                  <Colxx xxs="6">
                    <Card>
                      <div className="pl-4 pr-4 pt-4 justifyContent-cen alignItm-cen">
                        <h2>{column.name}</h2>
                      </div>
                      <CardBody
                      // style={{ border: '1px solid red', overflow: 'hidden' }}
                      >
                        <Droppable droppableId={columnId} key={columnId}>
                          {(provided, snapshot) => {
                            return (
                              <div
                                {...provided.droppableProps}
                                ref={provided.innerRef}
                                style={{
                                  background: snapshot.isDraggingOver
                                    ? 'lightblue'
                                    : 'lightgrey',
                                }}
                                className="p-4"
                              >
                                {column.items.map((item, index) => {
                                  return (
                                    <Draggable
                                      key={item.id}
                                      draggableId={item.id}
                                      index={index}
                                    >
                                      {(provided, snapshot) => {
                                        return (
                                          <div
                                            ref={provided.innerRef}
                                            {...provided.draggableProps}
                                            {...provided.dragHandleProps}
                                            //   style={{
                                            //     userSelect: 'none',
                                            //     padding: 16,
                                            //     margin: '0 0 8px 0',
                                            //     minHeight: '50px',
                                            //     backgroundColor: snapshot.isDragging
                                            //       ? '#263B4A'
                                            //       : '#456C86',
                                            //     color: 'white',
                                            //     ...provided.draggableProps.style,
                                            //   }}
                                          >
                                            {item.content}
                                          </div>
                                        );
                                      }}
                                    </Draggable>
                                  );
                                })}
                                {provided.placeholder}
                              </div>
                            );
                          }}
                        </Droppable>
                      </CardBody>
                    </Card>
                  </Colxx>
                </div>
              );
            })}
          </DragDropContext>
        </div>
      </Row>
    </div>
  );
}
