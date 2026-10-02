// Estilos globais primeiro: as telas sobrescrevem a partir deles.
import './index.css'
import { createRoot } from 'react-dom/client'
import { Aplicativo } from './Aplicativo'

const contêiner = document.getElementById('aplicativo')
if (contêiner) createRoot(contêiner).render(<Aplicativo />)
