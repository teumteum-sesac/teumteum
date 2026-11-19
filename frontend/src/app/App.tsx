import { RouterProvider } from 'react-router'
import Splash from '../shared/components/Splash'
import { router } from './router'

export default function App() {
  return (
    <>
      <RouterProvider router={router} />
      {/* 첫 접속 스플래시: 앱 위에 덮었다가 사라진다 */}
      <Splash />
    </>
  )
}
